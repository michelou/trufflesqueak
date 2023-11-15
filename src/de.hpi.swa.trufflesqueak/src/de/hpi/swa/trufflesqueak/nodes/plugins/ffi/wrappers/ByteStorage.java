package de.hpi.swa.trufflesqueak.nodes.plugins.ffi.wrappers;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import de.hpi.swa.trufflesqueak.util.UnsafeUtils;

@ExportLibrary(InteropLibrary.class)
public class ByteStorage extends NativeObjectStorage {
    byte[] storage;

    public ByteStorage(byte[] storage) {
        this.storage = storage;
    }

    @Override
    protected void allocate() {
        nativeAddress = UnsafeUtils.allocateNativeBytes(storage);
    }

    @Override
    public void cleanup() {
        UnsafeUtils.copyNativeBytesBackAndFree(nativeAddress, storage);
    }
}
