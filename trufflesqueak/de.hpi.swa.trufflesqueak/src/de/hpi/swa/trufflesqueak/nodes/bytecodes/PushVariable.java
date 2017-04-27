package de.hpi.swa.trufflesqueak.nodes.bytecodes;

import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.nodes.context.ContextAccessNode;
import de.hpi.swa.trufflesqueak.nodes.context.FrameSlotWriteNode;
import de.hpi.swa.trufflesqueak.nodes.context.MethodLiteralNodeGen;

public class PushVariable extends StackBytecodeNode {
    public PushVariable(CompiledMethodObject cm, int idx, int i) {
        super(cm, idx, createChild(cm, i & 31), +1);
    }

    public static ContextAccessNode createChild(CompiledMethodObject cm, int literalIdx) {
        return FrameSlotWriteNode.push(cm, MethodLiteralNodeGen.create(cm, literalIdx));
    }
}
