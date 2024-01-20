/*
 * Copyright (c) 2021-2024 Software Architecture Group, Hasso Plattner Institute
 * Copyright (c) 2021-2024 Oracle and/or its affiliates
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.interrupts;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DenyReplace;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.trufflesqueak.nodes.AbstractNode;
import de.hpi.swa.trufflesqueak.nodes.process.SignalSemaphoreNode;
import de.hpi.swa.trufflesqueak.util.LogUtils;

public abstract class CheckForInterruptsQuickNode extends AbstractNode {
    private static final int MIN_NUMBER_OF_BYTECODE_FOR_INTERRUPT_CHECKS = 32;

    public static final CheckForInterruptsQuickNode createForSend(final CompiledCodeObject code) {
        final SqueakImageContext image = code.getSqueakClass().getImage();
        /*
         * Only check for interrupts if method is relatively large. Avoid check if primitive method
         * or if a closure is activated (effectively what #primitiveClosureValueNoContextSwitch is
         * for).
         */
        if (image.interruptHandlerDisabled() || code.hasPrimitive() || //
                        code.getBytes().length < MIN_NUMBER_OF_BYTECODE_FOR_INTERRUPT_CHECKS || //
                        /* FullBlockClosure or normal closure */
                        code.isCompiledBlock() || code.hasOuterMethod()) {
            return NoCheckForInterruptsNode.SINGLETON;
        } else {
            return CheckForInterruptsQuickImplNode.SINGLETON;
        }
    }

    public static final CheckForInterruptsQuickNode createForLoop() {
        return CheckForInterruptsQuickImplNode.SINGLETON;
    }

    public abstract void execute(VirtualFrame frame);

    @DenyReplace
    private static final class NoCheckForInterruptsNode extends CheckForInterruptsQuickNode {
        private static final NoCheckForInterruptsNode SINGLETON = new NoCheckForInterruptsNode();

        @Override
        public void execute(final VirtualFrame frame) {
            // nothing to do
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        public Node copy() {
            return SINGLETON;
        }

        @Override
        public Node deepCopy() {
            return SINGLETON;
        }
    }

    @DenyReplace
    public static final class CheckForInterruptsQuickImplNode extends CheckForInterruptsQuickNode {
        private static final CheckForInterruptsQuickImplNode SINGLETON = new CheckForInterruptsQuickImplNode();

        private CheckForInterruptsQuickImplNode() {
        }

        @NeverDefault
        public static CheckForInterruptsQuickImplNode create() {
            return SINGLETON;
        }

        @Override
        public void execute(final VirtualFrame frame) {
            final SqueakImageContext image = getContext();
            final CheckForInterruptsState istate = image.interrupt;
            if (!istate.shouldTrigger()) {
                return;
            }
            /* Exclude interrupts case from compilation. */
            CompilerDirectives.transferToInterpreter();
            istate.resetTrigger();
            final Object[] specialObjects = image.specialObjectsArray.getObjectStorage();
            if (istate.interruptPending()) {
                LogUtils.INTERRUPTS.fine("User interrupt");
                istate.interruptPending = false; // reset interrupt flag
                SignalSemaphoreNode.executeUncached(frame, image, specialObjects[SPECIAL_OBJECT.THE_INTERRUPT_SEMAPHORE]);
            }
            if (istate.nextWakeUpTickTrigger()) {
                LogUtils.INTERRUPTS.fine("Timer interrupt");
                istate.nextWakeupTick = 0; // reset timer interrupt
                SignalSemaphoreNode.executeUncached(frame, image, specialObjects[SPECIAL_OBJECT.THE_TIMER_SEMAPHORE]);
            }
            if (istate.pendingFinalizationSignals()) { // signal any pending finalizations
                LogUtils.INTERRUPTS.fine("Finalization interrupt");
                istate.setPendingFinalizations(false);
                SignalSemaphoreNode.executeUncached(frame, image, specialObjects[SPECIAL_OBJECT.THE_FINALIZATION_SEMAPHORE]);
            }
            if (istate.hasSemaphoresToSignal()) {
                LogUtils.INTERRUPTS.fine("Semaphore interrupt");
                final ArrayObject externalObjects = (ArrayObject) specialObjects[SPECIAL_OBJECT.EXTERNAL_OBJECTS_ARRAY];
                if (!externalObjects.isEmptyType()) { // signal external semaphores
                    final Object[] semaphores = externalObjects.getObjectStorage();
                    Integer semaIndex;
                    while ((semaIndex = istate.nextSemaphoreToSignal()) != null) {
                        SignalSemaphoreNode.executeUncached(frame, image, semaphores[semaIndex - 1]);
                    }
                }
            }
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        public Node copy() {
            return SINGLETON;
        }

        @Override
        public Node deepCopy() {
            return copy();
        }
    }
}
