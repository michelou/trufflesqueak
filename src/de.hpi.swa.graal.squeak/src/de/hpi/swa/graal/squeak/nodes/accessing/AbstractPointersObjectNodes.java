package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractPointersObject;
import de.hpi.swa.graal.squeak.model.Location;
import de.hpi.swa.graal.squeak.model.Location.IllegalWriteException;
import de.hpi.swa.graal.squeak.model.Location.ReadLocationNode;
import de.hpi.swa.graal.squeak.model.Location.WriteLocationNode;
import de.hpi.swa.graal.squeak.model.ObjectLayout;
import de.hpi.swa.graal.squeak.model.VariablePointersObject;
import de.hpi.swa.graal.squeak.model.WeakVariablePointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodesFactory.AbstractPointersObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodesFactory.AbstractPointersObjectWriteNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodesFactory.VariablePointersObjectWriteNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodesFactory.WeakVariablePointersObjectWriteNodeGen;

public class AbstractPointersObjectNodes {
    protected static final int CACHE_LIMIT = 6;

    @GenerateUncached
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class AbstractPointersObjectReadNode extends AbstractNode {

        public static AbstractPointersObjectReadNode create() {
            return AbstractPointersObjectReadNodeGen.create();
        }

        public static AbstractPointersObjectReadNode getUncached() {
            return AbstractPointersObjectReadNodeGen.getUncached();
        }

        public abstract Object execute(AbstractPointersObject obj, int index);

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout"}, //
                        assumptions = "cachedLayout.getValidAssumption()", limit = "CACHE_LIMIT")
        protected static final Object doReadCached(final AbstractPointersObject object, final int index,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("cachedLayout.getLocation(index)") final Location cachedLocation,
                        @Cached final ReadLocationNode readNode) {
            return readNode.execute(cachedLocation, object);
        }

        @Specialization(guards = "object.getLayout().isValid()", replaces = {"doReadCached"})
        protected static final Object doReadUncached(final AbstractPointersObject object, final int index,
                        @Cached final ReadLocationNode readNode) {
            return readNode.execute(object.getLayout().getLocation(index), object);
        }

        @Specialization(guards = "!object.getLayout().isValid()")
        protected static final Object doUpdateLayoutAndRead(final AbstractPointersObject object, final int index) {
            CompilerDirectives.transferToInterpreter();
            object.updateLayout();
            return doReadUncached(object, index, ReadLocationNode.getUncached());
        }
    }

    @GenerateUncached
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class AbstractPointersObjectWriteNode extends AbstractNode {

        public static AbstractPointersObjectWriteNode create() {
            return AbstractPointersObjectWriteNodeGen.create();
        }

        public static AbstractPointersObjectWriteNode getUncached() {
            return AbstractPointersObjectWriteNodeGen.getUncached();
        }

        public abstract void execute(AbstractPointersObject obj, int index, Object value);

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout",
                        "!cachedLocation.isUninitialized()", "cachedLocation.canStore(value)"}, //
                        assumptions = "cachedLayout.getValidAssumption()", limit = "CACHE_LIMIT")
        protected static final void doWriteCached(final AbstractPointersObject object, final int index,
                        final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("cachedLayout.getLocation(index)") final Location cachedLocation,
                        @Cached final WriteLocationNode writeNode) {
            try {
                writeNode.execute(cachedLocation, object, value);
            } catch (final IllegalWriteException e) {
                throw SqueakException.illegalState(e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == oldLayout", "oldLocation.isUninitialized()"}, //
                        assumptions = {"oldLayout.getValidAssumption()", "newLayout.getValidAssumption()"}, limit = "CACHE_LIMIT")
        protected static final void doWriteCachedUninitialized(final AbstractPointersObject object, final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout oldLayout,
                        @Cached("oldLayout.getLocation(index)") final Location oldLocation,
                        @Cached("object.updateLayout(index, value)") final ObjectLayout newLayout,
                        @Cached("newLayout.getLocation(index)") final Location newLocation) {
            try {
                newLocation.write(object, value);
            } catch (final IllegalWriteException e) {
                throw SqueakException.illegalState(e);
            }
        }

        @Specialization(guards = "object.getLayout().isValid()", replaces = {"doWriteCached", "doWriteCachedUninitialized"})
        protected static final void doWriteUncached(final AbstractPointersObject object, final int index, final Object value,
                        @Cached final WriteLocationNode writeNode) {
            try {
                writeNode.execute(object.getLayout().getLocation(index), object, value);
            } catch (final IllegalWriteException e) {
                object.updateLayout(index, value);
                writeNode.execute(object.getLayout().getLocation(index), object, value);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "!object.getLayout().isValid()")
        protected static final void doUpdateLayoutAndWrite(final AbstractPointersObject object, final int index, final Object value) {
            CompilerDirectives.transferToInterpreter();
            object.updateLayout();
            doWriteUncached(object, index, value, WriteLocationNode.getUncached());
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class VariablePointersObjectReadNode extends Node {

        public abstract Object execute(VariablePointersObject object, int index);

        @Specialization(guards = "index < object.instsize()")
        protected static final Object doRead(final VariablePointersObject object, final int index,
                        @Cached final AbstractPointersObjectReadNode readNode) {
            return readNode.execute(object, index);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final Object doReadFromVariablePartCached(final VariablePointersObject object, @SuppressWarnings("unused") final int index,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            return object.getFromVariablePart(cachedIndex - cachedLayout.getInstSize());
        }

        @Specialization(guards = "index >= object.instsize()", replaces = "doReadFromVariablePartCached")
        protected static final Object doReadFromVariablePart(final VariablePointersObject object, final int index) {
            return object.getFromVariablePart(index - object.instsize());
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class VariablePointersObjectWriteNode extends Node {

        public static VariablePointersObjectWriteNode getUncached() {
            return VariablePointersObjectWriteNodeGen.getUncached();
        }

        public abstract void execute(VariablePointersObject object, int index, Object value);

        @Specialization(guards = "index < object.instsize()")
        protected static final void doWrite(final VariablePointersObject object, final int index, final Object value,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(object, index, value);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final void doWriteIntoVariablePartCached(final VariablePointersObject object, @SuppressWarnings("unused") final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            object.putIntoVariablePart(cachedIndex - cachedLayout.getInstSize(), value);
        }

        @Specialization(guards = "index >= object.instsize()", replaces = "doWriteIntoVariablePartCached")
        protected static final void doWriteIntoVariablePart(final VariablePointersObject object, final int index, final Object value) {
            object.putIntoVariablePart(index - object.instsize(), value);
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class WeakVariablePointersObjectReadNode extends Node {

        public abstract Object execute(WeakVariablePointersObject object, int index);

        @Specialization(guards = "index < object.instsize()")
        protected static final Object doRead(final WeakVariablePointersObject object, final int index,
                        @Cached final AbstractPointersObjectReadNode readNode) {
            return readNode.execute(object, index);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final Object doReadFromVariablePartCached(final WeakVariablePointersObject object, @SuppressWarnings("unused") final int index,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("createBinaryProfile()") final ConditionProfile nilProfile) {
            return object.getFromVariablePart(cachedIndex - cachedLayout.getInstSize(), nilProfile);
        }

        @Specialization(guards = "index >= object.instsize()", replaces = "doReadFromVariablePartCached")
        protected static final Object doReadFromVariablePart(final WeakVariablePointersObject object, final int index,
                        @Cached("createBinaryProfile()") final ConditionProfile nilProfile) {
            return object.getFromVariablePart(index - object.instsize(), nilProfile);
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class WeakVariablePointersObjectWriteNode extends Node {

        public static WeakVariablePointersObjectWriteNode getUncached() {
            return WeakVariablePointersObjectWriteNodeGen.getUncached();
        }

        public abstract void execute(WeakVariablePointersObject object, int index, Object value);

        @Specialization(guards = "index < object.instsize()")
        protected static final void doWrite(final WeakVariablePointersObject object, final int index, final Object value,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(object, index, value);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final void doWriteIntoVariablePartCached(final WeakVariablePointersObject object, @SuppressWarnings("unused") final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            object.putIntoVariablePart(cachedIndex - cachedLayout.getInstSize(), value);
        }

        @Specialization(guards = "index >= object.instsize()", replaces = "doWriteIntoVariablePartCached")
        protected static final void doWriteIntoVariablePart(final WeakVariablePointersObject object, final int index, final Object value) {
            object.putIntoVariablePart(index - object.instsize(), value);
        }
    }
}
