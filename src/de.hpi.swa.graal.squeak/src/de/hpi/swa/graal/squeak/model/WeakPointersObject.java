/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.model;

import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.ObjectGraphNode.ObjectTracer;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.WeakPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.accessing.UpdateSqueakObjectHashNode;
import de.hpi.swa.graal.squeak.util.UnsafeUtils;

public final class WeakPointersObject extends AbstractPointersObject {
    private static final WeakReference<?> NIL_REFERENCE = new WeakReference<>(NilObject.SINGLETON);
    @CompilationFinal(dimensions = 0) public WeakReference<?>[] variablePart;

    public WeakPointersObject(final SqueakImageContext image, final long hash, final ClassObject classObject) {
        super(image, hash, classObject);
    }

    public WeakPointersObject(final SqueakImageContext image, final ClassObject classObject, final int variableSize) {
        super(image, classObject);
        variablePart = new WeakReference<?>[variableSize];
        Arrays.fill(variablePart, NIL_REFERENCE);
    }

    private WeakPointersObject(final WeakPointersObject original) {
        super(original);
        variablePart = original.variablePart.clone();
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        final WeakPointersObjectWriteNode writeNode = WeakPointersObjectWriteNode.getUncached();
        final Object[] pointersObject = chunk.getPointers();
        initializeLayoutAndExtensionsUnsafe();
        final int instSize = getSqueakClass().getBasicInstanceSize();
        for (int i = 0; i < instSize; i++) {
            writeNode.execute(this, i, pointersObject[i]);
        }
        variablePart = new WeakReference<?>[pointersObject.length - instSize];
        for (int i = instSize; i < pointersObject.length; i++) {
            variablePart[i - instSize] = new WeakReference<>(pointersObject[i]);
        }
        assert size() == pointersObject.length;
    }

    public void become(final WeakPointersObject other) {
        becomeLayout(other);
        final Object[] otherVariablePart = other.variablePart;
        /*
         * Keep outer arrays and only copy contents as variablePart is marked
         * with @CompilationFinal(dimensions = 0).
         */
        System.arraycopy(variablePart, 0, other.variablePart, 0, variablePart.length);
        System.arraycopy(otherVariablePart, 0, variablePart, 0, otherVariablePart.length);
    }

    @Override
    public int size() {
        return instsize() + variablePart.length;
    }

    public void pointersBecomeOneWay(final UpdateSqueakObjectHashNode updateHashNode, final Object[] from, final Object[] to, final boolean copyHash) {
        layoutValuesBecomeOneWay(updateHashNode, from, to, copyHash);
        final int variableSize = variablePart.length;
        if (variableSize > 0) {
            for (int i = 0; i < from.length; i++) {
                final Object fromPointer = from[i];
                for (int j = 0; j < variableSize; j++) {
                    final Object object = getFromVariablePart(j);
                    if (object == fromPointer) {
                        putIntoVariablePart(j, to[i]);
                        updateHashNode.executeUpdate(fromPointer, to[i], copyHash);
                    }
                }
            }
        }
    }

    public Object getFromVariablePart(final int index) {
        return NilObject.nullToNil(UnsafeUtils.getWeakReference(variablePart, index).get());
    }

    public Object getFromVariablePart(final int index, final ConditionProfile nilProfile) {
        return NilObject.nullToNil(UnsafeUtils.getWeakReference(variablePart, index).get(), nilProfile);
    }

    public void putIntoVariablePart(final int index, final Object value) {
        UnsafeUtils.putWeakReference(variablePart, index, new WeakReference<>(value, image.weakPointersQueue));
    }

    public boolean pointsTo(final Object thang) {
        return layoutValuesPointTo(thang) || variablePartPointsTo(thang);
    }

    private boolean variablePartPointsTo(final Object thang) {
        for (final WeakReference<?> weakRef : variablePart) {
            if (weakRef.get() == thang) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "WeakPointersObject: " + getSqueakClass();
    }

    public WeakPointersObject shallowCopy() {
        return new WeakPointersObject(this);
    }

    public void traceObjects(final ObjectTracer tracer) {
        super.traceLayoutObjects(tracer);
    }
}
