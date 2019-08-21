package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;

public abstract class SenderForDispatchNode extends AbstractNodeWithCode {
    protected SenderForDispatchNode(final CompiledCodeObject code) {
        super(code);
    }

    public static SenderForDispatchNode create(final CompiledCodeObject code) {
        return SenderForDispatchNodeGen.create(code);
    }

    public abstract Object execute(VirtualFrame frame, CompiledCodeObject codeObject);

    @Specialization(guards = "codeObject.isUnwindMarked() || !codeObject.getDoesNotNeedSenderAssumption().isValid()")
    protected static final Object doEnsureContext(final VirtualFrame frame, @SuppressWarnings("unused") final CompiledCodeObject codeObject,
                    @Cached("create(code)") final GetOrCreateContextNode getOrCreateContextNode) {
        return getOrCreateContextNode.executeGet(frame);
    }

    @Fallback
    protected final Object doContextOrMarker(final VirtualFrame frame, @SuppressWarnings("unused") final CompiledCodeObject codeObject) {
        return getContextOrMarker(frame);
    }
}
