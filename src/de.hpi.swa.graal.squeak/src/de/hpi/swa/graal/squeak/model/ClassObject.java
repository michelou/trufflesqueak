package de.hpi.swa.graal.squeak.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.CyclicAssumption;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageReader;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CLASS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CLASS_DESCRIPTION;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.METACLASS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.METHOD_DICT;
import de.hpi.swa.graal.squeak.nodes.NewObjectNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

/*
 * Represents all subclasses of ClassDescription (Class, Metaclass, TraitBehavior, ...).
 */
@ExportLibrary(SqueakObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class ClassObject extends AbstractSqueakObjectWithClassAndHash {
    private final CyclicAssumption classHierarchyStable = new CyclicAssumption("Class hierarchy stability");
    private final CyclicAssumption methodDictStable = new CyclicAssumption("Method dictionary stability");
    private final CyclicAssumption classFormatStable = new CyclicAssumption("Class format stability");

    @CompilationFinal private boolean instancesAreClasses = false;

    private ClassObject superclass;
    @CompilationFinal private PointersObject methodDict;
    @CompilationFinal private long format = -1;
    private ArrayObject instanceVariables;
    private PointersObject organization;
    private Object[] pointers;

    public ClassObject(final SqueakImageContext image) {
        super(image);
    }

    public ClassObject(final SqueakImageContext image, final int hash) {
        super(image, hash);
    }

    public ClassObject(final SqueakImageContext image, final int hash, final ClassObject squeakClass) {
        super(image, hash, squeakClass);
    }

    private ClassObject(final ClassObject original, final ArrayObject copiedInstanceVariablesOrNull) {
        this(original.image, original.getSqueakClass(), original.pointers.clone());
        instancesAreClasses = original.instancesAreClasses;
        superclass = original.superclass;
        methodDict = original.methodDict.shallowCopy();
        format = original.format;
        instanceVariables = copiedInstanceVariablesOrNull;
        organization = original.organization == null ? null : original.organization.shallowCopy();
        pointers = original.pointers.clone();
    }

    private ClassObject(final SqueakImageContext image, final ClassObject sqClass, final Object[] pointers) {
        super(image, sqClass);
        this.pointers = pointers;
        instancesAreClasses = sqClass.isMetaClass();
    }

    public ClassObject(final SqueakImageContext image, final ClassObject classObject, final int size) {
        this(image, classObject, ArrayUtils.withAll(Math.max(size - CLASS_DESCRIPTION.SIZE, 0), NilObject.SINGLETON));
        // `size - CLASS_DESCRIPTION.SIZE` is negative when instantiating "Behavior".
    }

    @Override
    public String getClassName() {
        CompilerAsserts.neverPartOfCompilation();
        assert isClass();
        if (isAMetaClass()) {
            final Object classInstance = pointers[METACLASS.THIS_CLASS];
            if (classInstance != NilObject.SINGLETON) {
                return "Metaclass (" + ((ClassObject) classInstance).getClassNameUnsafe() + ")";
            } else {
                return "Metaclass (unknown)";
            }
        } else if (size() == 11) {
            return getClassNameUnsafe();
        } else {
            return "Unknown behavior";
        }
    }

    public String getClassNameUnsafe() {
        return ((NativeObject) pointers[CLASS.NAME]).asStringUnsafe();
    }

    private boolean isAMetaClass() {
        return getSqueakClass().isMetaClass();
    }

    public boolean isBits() {
        return getInstanceSpecification() >= 7;
    }

    public boolean isBytes() {
        return getInstanceSpecification() >= 16;
    }

    public boolean isCompiledMethodClassType() {
        return getInstanceSpecification() >= 24;
    }

    public boolean isEphemeronClassType() {
        return getInstanceSpecification() == 5;
    }

    public boolean isFixed() {
        return !isVariable();
    }

    public boolean isImmediateClassType() {
        return getInstanceSpecification() == 7;
    }

    public boolean isIndexableWithInstVars() {
        return getInstanceSpecification() == 3;
    }

    public boolean isIndexableWithNoInstVars() {
        return getInstanceSpecification() == 2;
    }

    public boolean isNonIndexableWithInstVars() {
        return getInstanceSpecification() == 1;
    }

    public boolean isLongs() {
        return getInstanceSpecification() == 9;
    }

    public boolean isPointers() {
        return !isBits();
    }

    public boolean isShorts() {
        return getInstanceSpecification() == 12;
    }

    public boolean isVariable() {
        final int instSpec = getInstanceSpecification();
        return instSpec >= 2 && (instSpec <= 4 || instSpec >= 9);
    }

    public boolean isWeak() {
        return getInstanceSpecification() == 4;
    }

    public boolean isWords() {
        return getInstanceSpecification() == 10;
    }

    public boolean isZeroSized() {
        return getInstanceSpecification() == 0;
    }

    public void setInstancesAreClasses() {
        if (!instancesAreClasses) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            instancesAreClasses = true;
        }
    }

    public boolean instancesAreClasses() {
        return instancesAreClasses;
    }

    public boolean isBitmapClass() {
        return this == image.bitmapClass;
    }

    public boolean isLargeIntegerClass() {
        return this == image.largePositiveIntegerClass || this == image.largeNegativeIntegerClass;
    }

    public boolean isMessageClass() {
        return this == image.messageClass;
    }

    public boolean isSemaphoreClass() {
        return this == image.semaphoreClass;
    }

    /** ByteString. */
    public boolean isStringClass() {
        return this == image.stringClass;
    }

    /** ByteSymbol. */
    public boolean isSymbolClass() {
        return this == image.aboutToReturnSelector.getSqueakClass();
    }

    /**
     * {@link ClassObject}s are filled in at an earlier stage in
     * {@link SqueakImageReader#fillInClassObjects}.
     */
    @Override
    public void fillin(final SqueakImageChunk chunk) {
        if (methodDict == null) {
            final Object[] chunkPointers = chunk.getPointers();
            superclass = chunkPointers[CLASS_DESCRIPTION.SUPERCLASS] == NilObject.SINGLETON ? null : (ClassObject) chunkPointers[CLASS_DESCRIPTION.SUPERCLASS];
            methodDict = (PointersObject) chunkPointers[CLASS_DESCRIPTION.METHOD_DICT];
            format = (long) chunkPointers[CLASS_DESCRIPTION.FORMAT];
            instanceVariables = chunkPointers[CLASS_DESCRIPTION.INSTANCE_VARIABLES] == NilObject.SINGLETON ? null : (ArrayObject) chunkPointers[CLASS_DESCRIPTION.INSTANCE_VARIABLES];
            organization = chunkPointers[CLASS_DESCRIPTION.ORGANIZATION] == NilObject.SINGLETON ? null : (PointersObject) chunkPointers[CLASS_DESCRIPTION.ORGANIZATION];
            pointers = Arrays.copyOfRange(chunkPointers, CLASS_DESCRIPTION.SIZE, chunkPointers.length);
            if (size() > 7) {
                final String className = getClassNameUnsafe();
                if (image.getCompilerClass() == null && "Compiler".equals(className)) {
                    image.setCompilerClass(this);
                } else if (image.getParserClass() == null && "Parser".equals(className)) {
                    image.setParserClass(this);
                } else if (!image.flags.is64bit() && image.smallFloatClass == null && "SmallFloat64".equals(className)) {
                    image.setSmallFloat(this);
                }
            }
        }
    }

    public void setFormat(final long format) {
        classFormatStable.invalidate();
        this.format = format;
    }

    public static boolean isSuperclassIndex(final long index) {
        return index == CLASS_DESCRIPTION.SUPERCLASS;
    }

    public static boolean isMethodDictIndex(final long index) {
        return index == CLASS_DESCRIPTION.METHOD_DICT;
    }

    public static boolean isFormatIndex(final long index) {
        return index == CLASS_DESCRIPTION.FORMAT;
    }

    public static boolean isInstanceVariablesIndex(final long index) {
        return index == CLASS_DESCRIPTION.INSTANCE_VARIABLES;
    }

    public static boolean isOrganizationIndex(final long index) {
        return index == CLASS_DESCRIPTION.ORGANIZATION;
    }

    public static boolean isOtherIndex(final long index) {
        return index >= CLASS_DESCRIPTION.SIZE;
    }

    public AbstractSqueakObject getSuperclass() {
        return NilObject.nullToNil(superclass);
    }

    public ClassObject getSuperclassOrNull() {
        return superclass;
    }

    public PointersObject getMethodDict() {
        return methodDict;
    }

    public boolean hasInstanceVariables() {
        return instanceVariables != null;
    }

    public AbstractSqueakObject getInstanceVariables() {
        return hasInstanceVariables() ? instanceVariables : NilObject.SINGLETON;
    }

    public ArrayObject getInstanceVariablesOrNull() {
        return instanceVariables;
    }

    public void setInstanceVariables(final ArrayObject instanceVariables) {
        this.instanceVariables = instanceVariables;
    }

    public AbstractSqueakObject getOrganization() {
        return NilObject.nullToNil(organization);
    }

    public PointersObject getOrganizationOrNull() {
        return organization;
    }

    public void setOrganization(final PointersObject organization) {
        this.organization = organization;
    }

    public Object getOtherPointer(final int index) {
        return pointers[index - CLASS_DESCRIPTION.SIZE];
    }

    public void setOtherPointer(final int index, final Object value) {
        pointers[index - CLASS_DESCRIPTION.SIZE] = value;
    }

    public Object[] getOtherPointers() {
        return pointers;
    }

    private void setOtherPointers(final Object[] pointers) {
        this.pointers = pointers;
    }

    public long getFormat() {
        return format;
    }

    public void setSuperclass(final ClassObject superclass) {
        classHierarchyStable.invalidate();
        this.superclass = superclass;
    }

    public void setMethodDict(final PointersObject methodDict) {
        methodDictStable.invalidate();
        this.methodDict = methodDict;
    }

    public Object[] listMethods() {
        CompilerAsserts.neverPartOfCompilation("This is only for the interop API.");
        final List<String> methodNames = new ArrayList<>();
        ClassObject lookupClass = this;
        while (lookupClass != null) {
            final PointersObject methodDictObject = lookupClass.getMethodDict();
            for (int i = METHOD_DICT.NAMES; i < methodDictObject.size(); i++) {
                final Object methodSelector = methodDictObject.at0(i);
                if (methodSelector != NilObject.SINGLETON) {
                    methodNames.add(methodSelector.toString());
                }
            }
            lookupClass = lookupClass.getSuperclassOrNull();
        }
        return methodNames.toArray(new String[methodNames.size()]);
    }

    public int getBasicInstanceSize() {
        return (int) (format & 0xffff);
    }

    public int getInstanceSpecification() {
        return (int) (format >> 16 & 0x1f);
    }

    public ClassObject shallowCopyWithInstVars(final ArrayObject copiedInstanceVariablesOrNull) {
        return new ClassObject(this, copiedInstanceVariablesOrNull);
    }

    public boolean pointsTo(final Object thang) {
        if (superclass == thang) {
            return true;
        }
        if (methodDict == thang) {
            return true;
        }
        if (thang instanceof Number && format == (long) thang) {
            return true; // TODO: check whether format needs to be checked
        }
        if (instanceVariables == thang) {
            return true;
        }
        if (organization == thang) {
            return true;
        }
        return ArrayUtils.contains(pointers, thang);
    }

    public void become(final ClassObject other) {
        becomeOtherClass(other);

        if (instancesAreClasses != other.getSqueakClass().isMetaClass()) {
            CompilerDirectives.transferToInterpreter();
            instancesAreClasses = other.getSqueakClass().isMetaClass();
        }

        final ClassObject otherSuperclass = other.superclass;
        final PointersObject otherMethodDict = other.methodDict;
        final long otherFormat = other.format;
        final ArrayObject otherInstanceVariables = other.instanceVariables;
        final PointersObject otherOrganization = other.organization;
        final Object[] otherPointers = other.pointers;

        other.setSuperclass(superclass);
        other.setMethodDict(methodDict);
        other.setFormat(format);
        other.setInstanceVariables(instanceVariables);
        other.setOrganization(organization);
        other.setOtherPointers(pointers);

        setSuperclass(otherSuperclass);
        setMethodDict(otherMethodDict);
        setFormat(otherFormat);
        setInstanceVariables(otherInstanceVariables);
        setOrganization(otherOrganization);
        setOtherPointers(otherPointers);
    }

    public Assumption getClassHierarchyStable() {
        return classHierarchyStable.getAssumption();
    }

    public Assumption getMethodDictStable() {
        return methodDictStable.getAssumption();
    }

    public void invalidateMethodDictStableAssumption() {
        methodDictStable.invalidate();
    }

    public Assumption getClassFormatStable() {
        return classFormatStable.getAssumption();
    }

    public String getClassComment() {
        return CLASS_DESCRIPTION.getClassComment(this);
    }

    public boolean isCompiledMethodClass() {
        return this == image.compiledMethodClass;
    }

    public boolean isMethodContextClass() {
        return this == image.methodContextClass;
    }

    public boolean isBlockClosureClass() {
        return this == image.blockClosureClass;
    }

    public boolean isFloatClass() {
        return this == image.floatClass;
    }

    public boolean isLargePositiveIntegerClass() {
        return this == image.largePositiveIntegerClass;
    }

    public boolean isLargeNegativeIntegerClass() {
        return this == image.largeNegativeIntegerClass;
    }

    public boolean isNilClass() {
        return this == image.nilClass;
    }

    public boolean isSmallIntegerClass() {
        return this == image.smallIntegerClass;
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean isInstantiable() {
        return true;
    }

    @ExportMessage
    protected Object instantiate(final Object[] arguments,
                    @CachedLibrary(limit = "2") final InteropLibrary functions,
                    @Cached(value = "create(this.image)", allowUncached = true) final NewObjectNode newObjectNode)
                    throws UnsupportedTypeException, ArityException {
        final int numArguments = arguments.length;
        switch (numArguments) {
            case 0:
                return newObjectNode.execute(this);
            case 1:
                if (functions.fitsInInt(arguments[0])) {
                    try {
                        return newObjectNode.execute(this, functions.asInt(arguments[0]));
                    } catch (final UnsupportedMessageException e) {
                        throw SqueakException.illegalState(e);
                    }
                } else {
                    throw UnsupportedTypeException.create(arguments, "Second argument must be the size as an integer.");
                }
            default:
                throw ArityException.create(1, numArguments);
        }
    }

    @ExportMessage
    public static class At0 {
        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final AbstractSqueakObject doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getSuperclass();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isMethodDictIndex(index)", "squeakClass == cachedSqueakClass"}, assumptions = {"cachedSqueakClass.getMethodDictStable()"}, limit = "3")
        protected static final PointersObject doClassMethodDictConstant(final ClassObject squeakClass, final int index,
                        @Cached("squeakClass") final ClassObject cachedSqueakClass,
                        @Cached("squeakClass.getMethodDict()") final PointersObject cachedMethodDict) {
            return cachedMethodDict;
        }

        @Specialization(guards = {"isMethodDictIndex(index)"}, replaces = "doClassMethodDictConstant")
        protected static final PointersObject doClassMethodDict(final ClassObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getMethodDict();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFormatIndex(index)", "squeakClass == cachedSqueakClass"}, assumptions = {"cachedSqueakClass.getClassFormatStable()"}, limit = "3")
        protected static final long doClassFormatConstant(final ClassObject squeakClass, final int index,
                        @Cached("squeakClass") final ClassObject cachedSqueakClass,
                        @Cached("squeakClass.getFormat()") final long cachedFormat) {
            return cachedFormat;
        }

        @Specialization(guards = {"isFormatIndex(index)"}, replaces = "doClassFormatConstant")
        protected static final long doClassFormat(final ClassObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getFormat();
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final AbstractSqueakObject doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getInstanceVariables();
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final AbstractSqueakObject doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getOrganization();
        }

        @Specialization(guards = "isOtherIndex(index)")
        protected static final Object doClass(final ClassObject obj, final int index) {
            return obj.getOtherPointer(index);
        }
    }

    @ExportMessage
    public static class Atput0 {
        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final void doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final int index, final ClassObject value) {
            obj.setSuperclass(value);
        }

        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final void doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.setSuperclass(null);
        }

        @Specialization(guards = "isMethodDictIndex(index)")
        protected static final void doClassMethodDict(final ClassObject obj, @SuppressWarnings("unused") final int index, final PointersObject value) {
            obj.setMethodDict(value);
        }

        @Specialization(guards = "isFormatIndex(index)")
        protected static final void doClassFormat(final ClassObject obj, @SuppressWarnings("unused") final int index, final long value) {
            obj.setFormat(value);
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final void doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final int index, final ArrayObject value) {
            obj.setInstanceVariables(value);
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final void doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.setInstanceVariables(null);
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final void doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final int index, final PointersObject value) {
            obj.setOrganization(value);
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final void doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.setOrganization(null);
        }

        @Specialization(guards = "isOtherIndex(index)")
        protected static final void doClass(final ClassObject obj, final int index, final Object value) {
            obj.setOtherPointer(index, value);
        }
    }

    @ExportMessage
    public int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @ExportMessage
    public int size() {
        return CLASS_DESCRIPTION.SIZE + pointers.length;
    }

    @ExportMessage
    public static class ShallowCopy {
        @Specialization(guards = "!receiver.hasInstanceVariables()")
        protected static final ClassObject doClassNoInstanceVariables(final ClassObject receiver) {
            return receiver.shallowCopyWithInstVars(null);
        }

        @Specialization(guards = "receiver.hasInstanceVariables()", limit = "1")
        protected static final ClassObject doClass(final ClassObject receiver,
                        @CachedLibrary("receiver.getInstanceVariablesOrNull()") final SqueakObjectLibrary objectLibrary) {
            return receiver.shallowCopyWithInstVars((ArrayObject) objectLibrary.shallowCopy(receiver.getInstanceVariablesOrNull()));
        }
    }
}
