package de.hpi.swa.trufflesqueak.nodes.bytecodes.store;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.WriteNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.SqueakBytecodeNode;
import de.hpi.swa.trufflesqueak.nodes.context.FrameReceiverNode;
import de.hpi.swa.trufflesqueak.nodes.context.ObjectAtPutNode;

public abstract class AbstractStoreIntoReceiverVariableNode extends SqueakBytecodeNode {
    @Child WriteNode storeNode;
    protected final int receiverIndex;

    public AbstractStoreIntoReceiverVariableNode(CompiledCodeObject code, int index, int numBytecodes, int receiverIndex) {
        super(code, index, numBytecodes);
        this.receiverIndex = receiverIndex;
        storeNode = ObjectAtPutNode.create(receiverIndex, new FrameReceiverNode(code));
    }
}
