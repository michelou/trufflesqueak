package de.hpi.swa.trufflesqueak.nodes.plugins.ffi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.NFIUtils;

import java.util.HashMap;
import java.util.Map;

public final class PrimExternalCallNode extends AbstractPrimitiveNode {
    private final Object moduleLibrary;
    private final InteropLibrary moduleInteropLibrary;
    private final Object functionSymbol;
    private final InteropLibrary functionInteropLibrary;
    private final int numReceiverAndArguments;
    private static final Map<String, Object> loadedLibraries = new HashMap<>();

    public PrimExternalCallNode(final Object moduleLibrary, final InteropLibrary moduleInteropLibrary, final Object functionSymbol, final InteropLibrary functionInteropLibrary,
                    final int numReceiverAndArguments) {
        this.moduleLibrary = moduleLibrary;
        this.moduleInteropLibrary = moduleInteropLibrary;
        this.functionSymbol = functionSymbol;
        this.functionInteropLibrary = functionInteropLibrary;
        this.numReceiverAndArguments = numReceiverAndArguments;
    }

    public static PrimExternalCallNode load(final String moduleName, final String functionName, final int numReceiverAndArguments)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
        final SqueakImageContext context = SqueakImageContext.getSlow();
        final Object moduleLibrary = loadedLibraries.computeIfAbsent(moduleName, (String s) -> NFIUtils.loadLibrary(context, moduleName, "{ " +
                        // TODO, see below
                        // "initialiseModule():SINT64; " +
                        "setInterpreter(POINTER):SINT64; " +
                        // Currently not called, since plugins are never unloaded
                        // "shutdownModule():SINT64; " +
                        " }"));
        if (moduleLibrary == null) {
            return null;
        }
        final InteropLibrary moduleInteropLibrary = NFIUtils.getInteropLibrary(moduleLibrary);

        final Object functionSymbol = NFIUtils.loadMember(context, moduleLibrary, functionName, "():SINT64");
        final InteropLibrary functionInteropLibrary = NFIUtils.getInteropLibrary(functionSymbol);

        return new PrimExternalCallNode(moduleLibrary, moduleInteropLibrary, functionSymbol, functionInteropLibrary, numReceiverAndArguments);
    }

    @Override
    public Object execute(final VirtualFrame frame) {
        return doExternalCall(frame.materialize());
    }

    @Override
    public Object executeWithArguments(final VirtualFrame frame, final Object... receiverAndArguments) {
        // arguments are handled via manipulation of the stack pointer, see below
        return execute(frame);
    }

    @TruffleBoundary
    private Object doExternalCall(final MaterializedFrame frame) {
        InterpreterProxy interpreterProxy = null;
        try {
            interpreterProxy = InterpreterProxy.instanceFor(getContext(), frame, numReceiverAndArguments);

            // A send (AbstractSendNode.executeVoid) will decrement the stack pointer by
            // numReceiverAndArguments
            // before transferring control. We need the stack pointer to point at the last argument,
            // since the C code expects that. Therefore, we undo the decrement operation here.
            FrameAccess.setStackPointer(frame, FrameAccess.getStackPointer(frame) + numReceiverAndArguments);

            // TODO: Only call when the plugin actually defines the function
            // uuidPluginLibrary.invokeMember(uuidPlugin, "initialiseModule");

            moduleInteropLibrary.invokeMember(moduleLibrary, "setInterpreter", InterpreterProxy.getPointer());

            // return value is unused, the actual return value is pushed onto the stack (see below)
            functionInteropLibrary.execute(functionSymbol);

            // The return value is pushed onto the stack by the plugin via the InterpreterProxy, but
            // TruffleSqueak expects the return value to be returned by this function
            // (AbstractSendNode.executeVoid). Pop the return value and return it.
            final Object returnValue = FrameAccess.getStackValue(frame, FrameAccess.getStackPointer(frame) - 1, FrameAccess.getNumArguments(frame));
            FrameAccess.setStackPointer(frame, FrameAccess.getStackPointer(frame) - 1);
            return returnValue;
        } catch (Exception e) {
            // for debugging purposes
            e.printStackTrace(System.err);
            throw PrimitiveFailed.GENERIC_ERROR;
        } finally {
            if (interpreterProxy != null) {
                interpreterProxy.postPrimitiveCleanups();
            }
        }
    }
}
