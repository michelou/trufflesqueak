package de.hpi.swa.trufflesqueak.util;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;

import java.io.File;

public class NFIUtils {

    @ExportLibrary(InteropLibrary.class)
    public static class TruffleExecutable implements TruffleObject {
        public String nfiSignature;
        ITruffleExecutable executable;

        public TruffleExecutable(String nfiSignature, ITruffleExecutable executable) {
            this.nfiSignature = nfiSignature;
            this.executable = executable;
        }

        public static<T, R> TruffleExecutable wrap(String nfiSignature, TruffleFunction<T, R> function) {
            return new TruffleExecutable(nfiSignature, function);
        }
        public static<S, T, R> TruffleExecutable wrap(String nfiSignature, TruffleBiFunction<S, T, R> function) {
            return new TruffleExecutable(nfiSignature, function);
        }

        public static<R> TruffleExecutable wrap(String nfiSignature, TruffleSupplier<R> supplier) {
            return new TruffleExecutable(nfiSignature, supplier);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object... arguments) {
            return executable.execute(arguments);
        }
    }

    public interface ITruffleExecutable {
        Object execute(Object... arguments);
    }

    @FunctionalInterface
    public interface TruffleFunction<T, R> extends ITruffleExecutable {
        R run(T argument);

        default Object execute(Object... arguments) {
            assert arguments.length == 1;
            return run((T) arguments[0]);
        }
    }

    @FunctionalInterface
    public interface TruffleBiFunction<S, T, R> extends ITruffleExecutable {
        R run(S argument1, T argument2);

        default Object execute(Object... arguments) {
            assert arguments.length == 2;
            return run((S) arguments[0], (T) arguments[1]);
        }
    }

    @FunctionalInterface
    public interface TruffleSupplier<R> extends ITruffleExecutable {
        R run();

        default Object execute(Object... arguments) {
            assert arguments.length == 0;
            return run();
        }
    }

    public static Object executeNFI(SqueakImageContext context, String nfiCode) {
        final Source source = Source.newBuilder("nfi", nfiCode, "native").build();
        return context.env.parseInternal(source).call();
    }

    public static Object loadLibrary(SqueakImageContext context, String moduleName, String boundSymbols) {
        final String libName = System.mapLibraryName(moduleName);
        final TruffleFile libPath = context.getHomePath().resolve("lib" + File.separatorChar + libName);
        if (!libPath.exists()) {
            throw PrimitiveFailed.GENERIC_ERROR;
        }
        final String nfiCode = "load \"" + libPath.getAbsoluteFile().getPath() + "\" " + boundSymbols;
        return executeNFI(context, nfiCode);
    }

    public static Object loadMember(SqueakImageContext context, Object library, String name, String signature) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
        InteropLibrary interopLibrary = getInteropLibrary(library);
        Object symbol = interopLibrary.readMember(library, name);
        Object nfiSignature = executeNFI(context, signature);
        InteropLibrary signatureInteropLibrary = getInteropLibrary(nfiSignature);
        return signatureInteropLibrary.invokeMember(nfiSignature, "bind", symbol);
    }

    public static InteropLibrary getInteropLibrary(Object loadedLibrary) {
        return InteropLibrary.getFactory().getUncached(loadedLibrary);
    }
}
