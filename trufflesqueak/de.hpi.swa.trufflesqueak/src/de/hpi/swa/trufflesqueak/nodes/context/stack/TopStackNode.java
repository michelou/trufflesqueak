package de.hpi.swa.trufflesqueak.nodes.context.stack;

import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackReadNode;

public class TopStackNode extends AbstractStackNode {
    @Child private FrameStackReadNode readNode;

    public TopStackNode(CompiledCodeObject code) {
        super(code);
        readNode = FrameStackReadNode.create();
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return readNode.execute(frame, stackPointer(frame));
    }
}
