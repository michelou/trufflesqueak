package de.hpi.swa.trufflesqueak.nodes.context;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.nodes.SqueakNode;

@NodeChildren({@NodeChild(value = "objectNode", type = SqueakNode.class)})
public abstract class ObjectAtNode extends AbstractObjectAtNode {
    @CompilationFinal private final ValueProfile classProfile = ValueProfile.createClassProfile();
    @CompilationFinal private final int index;

    public static ObjectAtNode create(int i, SqueakNode object) {
        return ObjectAtNodeGen.create(i, object);
    }

    protected ObjectAtNode(int variableIndex) {
        index = variableIndex;
    }

    public abstract Object executeGeneric(VirtualFrame frame);

    @Specialization
    protected Object read(NativeObject object) {
        return classProfile.profile(object).getNativeAt0(index);
    }

    @Specialization(guards = "!isNativeObject(object)")
    protected Object read(BaseSqueakObject object) {
        return classProfile.profile(object).at0(index);
    }
}
