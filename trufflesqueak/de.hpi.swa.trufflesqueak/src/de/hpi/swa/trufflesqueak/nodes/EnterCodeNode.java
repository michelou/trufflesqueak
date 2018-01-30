package de.hpi.swa.trufflesqueak.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.FrameMarker;

public abstract class EnterCodeNode extends RootNode {
    @CompilationFinal protected final CompiledCodeObject code;

    public static EnterCodeNode create(SqueakLanguage language, CompiledCodeObject code) {
        return EnterCodeNodeGen.create(language, code);
    }

    protected EnterCodeNode(SqueakLanguage language, CompiledCodeObject code) {
        super(language, code.getFrameDescriptor());
        this.code = code;
    }

    @ExplodeLoop
    @Specialization(assumptions = {"code.getNoContextNeededAssumption()"})
    protected Object enterVirtualized(VirtualFrame frame,
                    @Cached("create(code)") ExecuteContextNode contextNode) {
        CompilerDirectives.ensureVirtualized(frame);
        frame.setObject(code.thisContextOrMarkerSlot, new FrameMarker()); // storing new marker in slot
        int numTempsToInitialize = Math.max(code.getNumTemps() - code.getNumArgsAndCopiedValues(), 0);
        for (int i = 0; i < numTempsToInitialize; i++) {
            frame.setObject(code.stackSlots[i], code.image.nil);
        }
        frame.setInt(code.instructionPointerSlot, 0);
        frame.setInt(code.stackPointerSlot, code.getNumArgsAndCopiedValues() + numTempsToInitialize);
        return contextNode.executeVirtualized(frame);
    }

    @ExplodeLoop
    @Specialization(guards = {"!code.getNoContextNeededAssumption().isValid()"})
    protected Object enter(VirtualFrame frame,
                    @Cached("create(code)") ExecuteContextNode contextNode) {
        frame.setInt(code.instructionPointerSlot, 0);
        frame.setInt(code.stackPointerSlot, 0);
        ContextObject newContext = ContextObject.materialize(frame, new FrameMarker());
        frame.setObject(code.thisContextOrMarkerSlot, newContext);
        Object[] arguments = frame.getArguments();
        assert arguments.length - (FrameAccess.RCVR_AND_ARGS_START + 1) == code.getNumArgsAndCopiedValues();
        for (int i = FrameAccess.RCVR_AND_ARGS_START + 1; i < arguments.length; i++) {
            newContext.push(arguments[i]);
        }
        int numTempsToInitialize = code.getNumTemps() - code.getNumArgsAndCopiedValues();
        for (int i = 0; i < numTempsToInitialize; i++) {
            newContext.push(code.image.nil);
        }
        return contextNode.executeNonVirtualized(frame);
    }

    @Override
    public String getName() {
        return toString();
    }

    @Override
    public String toString() {
        return code.toString();
    }
}
