package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodesFactory.ContextObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodesFactory.ContextObjectWriteNodeGen;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackWriteNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class ContextObjectNodes {

    @ImportStatic(CONTEXT.class)
    public abstract static class ContextObjectReadNode extends Node {

        public static ContextObjectReadNode create() {
            return ContextObjectReadNodeGen.create();
        }

        public abstract Object execute(Frame frame, FrameMarker obj, long index);

        @Specialization(guards = {"!obj.matches(frame)"})
        protected static final Object doMaterialize(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return obj.getMaterializedContext().at0(index);
        }

        @Specialization(guards = {"obj.matches(frame)", "index == SENDER_OR_NIL"})
        protected static final Object doSenderVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            final Object senderOrMarker = frame.getArguments()[FrameAccess.SENDER_OR_SENDER_MARKER];
            if (senderOrMarker instanceof FrameMarker) {
                return doMaterialize(frame, obj, index); // FIXME: can we do better? iterframes?
            } else {
                return senderOrMarker;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == INSTRUCTION_POINTER"})
        protected static final Object doPCVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            final CompiledCodeObject blockOrMethod = FrameAccess.getMethod(frame);
            final int pc = FrameUtil.getIntSafe(frame, blockOrMethod.instructionPointerSlot);
            if (pc < 0) {
                return blockOrMethod.image.nil;
            } else {
                final int initalPC;
                if (blockOrMethod instanceof CompiledBlockObject) {
                    initalPC = ((CompiledBlockObject) blockOrMethod).getInitialPC();
                } else {
                    initalPC = ((CompiledMethodObject) blockOrMethod).getInitialPC();
                }
                return (long) (initalPC + pc);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == STACKPOINTER"})
        protected static final Object doSPVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            return (long) FrameUtil.getIntSafe(frame, FrameAccess.getMethod(frame).stackPointerSlot);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == METHOD"})
        protected static final CompiledCodeObject doMethodVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getMethod(frame);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final Object doClosureVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            final BlockClosureObject closure = FrameAccess.getClosure(frame);
            if (closure != null) {
                return closure;
            } else {
                return FrameAccess.getMethod(frame).image.nil; // TODO: make better
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == RECEIVER"})
        protected static final Object doReceiverVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getReceiver(frame);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index >= TEMP_FRAME_START"})
        protected static final Object doStackVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            final int stackIndex = (int) (index - CONTEXT.TEMP_FRAME_START);
            final CompiledCodeObject code = FrameAccess.getMethod(frame);
            if (stackIndex >= code.getNumStackSlots()) {
                return code.image.nil; // TODO: make this better.
            } else {
                return frame.getValue(code.getStackSlot(stackIndex));
            }
        }

        @Fallback
        protected static final long doFail(final FrameMarker obj, final long index) {
            throw new SqueakException("Unexpected values:", obj, index);
        }
    }

    @ImportStatic({CONTEXT.class, FrameAccess.class})
    public abstract static class ContextObjectWriteNode extends Node {

        public static ContextObjectWriteNode create() {
            return ContextObjectWriteNodeGen.create();
        }

        public abstract void execute(VirtualFrame frame, FrameMarker obj, long index, Object value);

        @Specialization(guards = {"!obj.matches(frame)"})
        protected static final void doMaterialize(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker obj, final long index, final Object value) {
            obj.getMaterializedContext().atput0(index, value);
        }

        @Specialization(guards = {"obj.matches(frame)", "index == SENDER_OR_NIL"})
        protected static final void doSenderVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final Object value) {
            // Bailing, frame needs to be materialized.
            doMaterialize(frame, obj, index, value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == INSTRUCTION_POINTER"})
        protected static final void doPCVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final NilObject value) {
            frame.setInt(FrameAccess.getMethod(frame).instructionPointerSlot, -1);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == INSTRUCTION_POINTER", "value >= 0"})
        protected static final void doPCVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final long value) {
            final CompiledCodeObject blockOrMethod = FrameAccess.getMethod(frame);
            final int initalPC;
            if (blockOrMethod instanceof CompiledBlockObject) {
                initalPC = ((CompiledBlockObject) blockOrMethod).getInitialPC();
            } else {
                initalPC = ((CompiledMethodObject) blockOrMethod).getInitialPC();
            }
            frame.setInt(FrameAccess.getMethod(frame).instructionPointerSlot, (int) value - initalPC);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == STACKPOINTER"})
        protected static final void doSPVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final long value) {
            frame.setInt(FrameAccess.getMethod(frame).stackPointerSlot, (int) value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == METHOD"})
        protected static final void doMethodVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final CompiledCodeObject value) {
            frame.getArguments()[FrameAccess.METHOD] = value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosureVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final NilObject value) {
            frame.getArguments()[FrameAccess.CLOSURE_OR_NULL] = null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosureVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final BlockClosureObject value) {
            frame.getArguments()[FrameAccess.CLOSURE_OR_NULL] = value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == RECEIVER"})
        protected static final void doReceiverVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final Object value) {
            frame.getArguments()[FrameAccess.RECEIVER] = value;
        }

        @Specialization(guards = {"obj.matches(frame)", "index >= TEMP_FRAME_START"})
        protected static final void doStackVirtualized(final VirtualFrame frame, @SuppressWarnings("unused") final FrameMarker obj, final long index, final Object value,
                        @Cached("create(getMethod(frame))") final FrameStackWriteNode writeNode) {
            final int stackIndex = (int) (index - CONTEXT.TEMP_FRAME_START);
            writeNode.execute(frame, stackIndex, value);
        }

        @Fallback
        protected static final void doFail(final FrameMarker obj, final long index, final Object value) {
            throw new SqueakException("Unexpected values:", obj, index, value);
        }
    }

}
