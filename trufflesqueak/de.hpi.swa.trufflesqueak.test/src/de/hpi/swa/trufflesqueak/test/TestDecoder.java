package de.hpi.swa.trufflesqueak.test;

import org.junit.Test;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.DupNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.SqueakBytecodeNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.jump.ConditionalJumpNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.push.PushConstantNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.returns.ReturnReceiverNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.send.SendSelectorNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.store.PopNode;
import de.hpi.swa.trufflesqueak.util.SqueakBytecodeDecoder;

public class TestDecoder extends TestSqueak {
	@Test
	public void testIfNil() {
		// (1 ifNil: [true]) class
		// pushConstant: 1, dup, pushConstant: nil, send: ==, jumpFalse: 24, pop,
		// pushConstant: true, send: class, pop, returnSelf
		int[] bytes = { 0x76, 0x88, 0x73, 0xc6, 0x99, 0x87, 0x71, 0xc7, 0x87, 0x78 };
		CompiledCodeObject code = makeMethod(bytes);
		SqueakBytecodeNode[] bytecodeNodes = new SqueakBytecodeDecoder(code).decode();
		assertEquals(bytes.length, bytecodeNodes.length);
		assertSame(PushConstantNode.class, bytecodeNodes[0].getClass());
		assertSame(DupNode.class, bytecodeNodes[1].getClass());
		assertSame(PushConstantNode.class, bytecodeNodes[2].getClass());

		SendSelectorNode send = (SendSelectorNode) bytecodeNodes[3];
		assertSame(image.equivalent, send.selector);

		assertSame(ConditionalJumpNode.class, bytecodeNodes[4].getClass());
		assertSame(PopNode.class, bytecodeNodes[5].getClass());
		assertSame(PushConstantNode.class, bytecodeNodes[6].getClass());

		send = (SendSelectorNode) bytecodeNodes[7];
		assertSame(image.klass, send.selector);

		assertSame(PopNode.class, bytecodeNodes[8].getClass());
		assertSame(ReturnReceiverNode.class, bytecodeNodes[9].getClass());
	}
}
