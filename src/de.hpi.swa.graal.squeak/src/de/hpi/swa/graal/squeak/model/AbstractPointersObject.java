package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
public abstract class AbstractPointersObject extends AbstractSqueakObjectWithClassAndHash {
    @CompilationFinal(dimensions = 0) protected Object[] pointers;

    protected AbstractPointersObject(final SqueakImageContext image) {
        super(image);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final ClassObject sqClass) {
        super(image, sqClass);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final long hash, final ClassObject sqClass) {
        super(image, hash, sqClass);
    }

    public final Object getPointer(final int index) {
        return pointers[index];
    }

    public final Object[] getPointers() {
        return pointers;
    }

    public final void setPointer(final int index, final Object value) {
        pointers[index] = value;
    }

    public final void setPointersUnsafe(final Object[] pointers) {
        this.pointers = pointers;
    }

    public final void setPointers(final Object[] pointers) {
        // TODO: find out if invalidation should be avoided by copying values if pointers != null
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.pointers = pointers;
    }

    @ExportMessage
    public final int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @ExportMessage
    public final int size() {
        return pointers.length;
    }
}
