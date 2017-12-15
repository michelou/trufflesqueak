package de.hpi.swa.trufflesqueak.nodes.context.stack;

import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.context.FrameStackReadNode;

public class StackTopNode extends AbstractStackNode {
    @Child private FrameStackReadNode readNode;

    public StackTopNode(CompiledCodeObject code) {
        super(code);
        readNode = FrameStackReadNode.create();
    }

    public Object execute(VirtualFrame frame) {
        int sp = stackPointer(frame);
        return readNode.execute(frame, sp);
    }
}
