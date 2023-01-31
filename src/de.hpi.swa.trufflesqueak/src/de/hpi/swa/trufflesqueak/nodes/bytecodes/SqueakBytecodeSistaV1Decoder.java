/*
 * Copyright (c) 2017-2023 Software Architecture Group, Hasso Plattner Institute
 * Copyright (c) 2021-2023 Oracle and/or its affiliates
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.bytecodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SPECIAL_OBJECT;

public final class SqueakBytecodeSistaV1Decoder extends AbstractSqueakBytecodeDecoder {
    public static final SqueakBytecodeSistaV1Decoder SINGLETON = new SqueakBytecodeSistaV1Decoder();

    private SqueakBytecodeSistaV1Decoder() {
    }

    @Override
    public int findLineNumber(final CompiledCodeObject code, final int targetIndex) {
        final int trailerPosition = trailerPosition(code);
        assert 0 <= targetIndex && targetIndex <= trailerPosition : targetIndex + " not between 0 and " + trailerPosition;
        int index = 0;
        int lineNumber = 1;
        while (index != targetIndex) {
            index += decodeNumBytes(code, index);
            lineNumber++;
        }
        assert lineNumber <= trailerPosition;
        return lineNumber;
    }

    @Override
    public int trailerPosition(final CompiledCodeObject code) {
        return code.isCompiledBlock() ? code.getBytes().length : trailerPosition(code.getBytes());
    }

    private static int trailerPosition(final byte[] bytecode) {
        final int bytecodeLength = bytecode.length;
        final int flagByte = Byte.toUnsignedInt(bytecode[bytecodeLength - 1]);
        final int index = (flagByte >> 2) + 1;
        return switch (index) {
            // #decodeNoTrailer and #decodeSourceBySelector
            case 1, 5 -> bytecodeLength - 1;
            // #decodeClearedTrailer, #decodeTempsNamesQCompress, #decodeTempsNamesZip,
            // #decodeSourceByStringIdentifier, #decodeEmbeddedSourceQCompress, and
            // #decodeEmbeddedSourceZip
            case 2, 3, 4, 6, 7, 8 -> decodeLengthField(bytecode, bytecodeLength, flagByte);
            // #decodeVarLengthSourcePointer
            case 9 -> {
                int pos = bytecodeLength - 2;
                while (bytecode[pos] < 0) {
                    pos--;
                }
                yield pos;
            }
            // #decodeSourcePointer
            case 64 -> bytecodeLength - 4;
            default -> throw SqueakException.create("Undefined method encoding (see CompiledMethodTrailer).");
        };
    }

    private static int decodeLengthField(final byte[] bytecode, final int bytecodeLength, final int flagByte) {
        final int numBytes = (flagByte & 3) + 1;
        int length = 0;
        final int firstLengthValueIndex = bytecodeLength - 2;
        for (int i = 0; i < numBytes; i++) {
            length = (length << 8) + Byte.toUnsignedInt(bytecode[firstLengthValueIndex - i]);
        }
        return bytecodeLength - (1 + numBytes + length);
    }

    @Override
    public boolean hasStoreIntoTemp1AfterCallPrimitive(final CompiledCodeObject code) {
        final byte[] bytes = code.getBytes();
        return Byte.toUnsignedInt(bytes[3]) == 245;
    }

    @Override
    public AbstractBytecodeNode decodeBytecode(final VirtualFrame frame, final CompiledCodeObject code, final int index) {
        return decodeBytecode(frame, code, index, 0, 0, 0, 0);
    }

    private static AbstractBytecodeNode decodeBytecode(final VirtualFrame frame, final CompiledCodeObject code, final int index, final int extBytes, final int extA, final int extB,
                    final int numExtB) {
        CompilerAsserts.neverPartOfCompilation();
        final byte[] bytecode = code.getBytes();
        final int indexWithExt = index + extBytes;
        final int b = Byte.toUnsignedInt(bytecode[indexWithExt]);
        return switch (b) {
            case 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F //
                            -> PushBytecodes.PushReceiverVariableNode.create(code, index, 1, b & 0xF);
            case 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F //
                            -> PushBytecodes.PushLiteralVariableNode.create(code, index, 1, b & 0xF);
            case 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F //
                            -> new PushBytecodes.PushLiteralConstantNode(code, index, 1, b & 0x1F);
            case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47 -> new PushBytecodes.PushTemporaryLocationNode(code, index, 1, b & 0x7);
            case 0x48, 0x49, 0x4A, 0x4B -> new PushBytecodes.PushTemporaryLocationNode(code, index, 1, (b & 3) + 8);
            case 0x4C -> PushBytecodes.PushReceiverNode.create(code, index);
            case 0x4D -> new PushBytecodes.PushConstantTrueNode(code, index);
            case 0x4E -> new PushBytecodes.PushConstantFalseNode(code, index);
            case 0x4F -> new PushBytecodes.PushConstantNilNode(code, index);
            case 0x50 -> new PushBytecodes.PushConstantZeroNode(code, index);
            case 0x51 -> new PushBytecodes.PushConstantOneNode(code, index);
            case 0x52 -> {
                if (extB == 0) { // Push thisContext, (then Extend B = 1 => push thisProcess)
                    yield new PushBytecodes.PushActiveContextNode(code, index);
                } else {
                    yield new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 1, b);
                }
            }
            case 0x53 -> new MiscellaneousBytecodes.DupNode(code, index);
            case 0x54, 0x55, 0x56, 0x57 -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 1, b);
            case 0x58 -> new ReturnBytecodes.ReturnReceiverNode(frame, code, index);
            case 0x59 -> new ReturnBytecodes.ReturnConstantTrueNode(frame, code, index);
            case 0x5A -> new ReturnBytecodes.ReturnConstantFalseNode(frame, code, index);
            case 0x5B -> new ReturnBytecodes.ReturnConstantNilNode(frame, code, index);
            case 0x5C -> new ReturnBytecodes.ReturnTopFromMethodNode(frame, code, index);
            case 0x5D -> new ReturnBytecodes.ReturnNilFromBlockNode(code, index);
            case 0x5E -> {
                if (extA == 0) {
                    yield new ReturnBytecodes.ReturnTopFromBlockNode(code, index);
                } else { // shouldBeImplemented, see #genExtReturnTopFromBlock
                    yield new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 1, b);
                }
            }
            case 0x5F -> new MiscellaneousBytecodes.NopBytecodeNode(code, index);
            case 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F //
                            -> SendBytecodes.AbstractSendSpecialSelectorQuickNode.create(frame, code, index, b - 96);
            case 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F //
                            -> SendBytecodes.SelfSendNode.create(code, index, 1, (NativeObject) code.getLiteral(b & 0xF), 0);
            case 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F //
                            -> SendBytecodes.SelfSendNode.create(code, index, 1, (NativeObject) code.getLiteral(b & 0xF), 1);
            case 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF //
                            -> SendBytecodes.SelfSendNode.create(code, index, 1, (NativeObject) code.getLiteral(b & 0xF), 2);
            case 0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7 -> JumpBytecodes.UnconditionalJumpNode.createShort(code, index, b);
            case 0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF -> JumpBytecodes.ConditionalJumpOnTrueNode.createShort(code, index, b);
            case 0xC0, 0xC1, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7 -> JumpBytecodes.ConditionalJumpOnFalseNode.createShort(code, index, b);
            case 0xC8, 0xC9, 0xCA, 0xCB, 0xCC, 0xCD, 0xCE, 0xCF -> new StoreBytecodes.PopIntoReceiverVariableNode(code, index, 1, b & 7);
            case 0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7 -> new StoreBytecodes.PopIntoTemporaryLocationNode(code, index, 1, b & 7);
            case 0xD8 -> new MiscellaneousBytecodes.PopNode(code, index);
            // FIXME: Unconditional trap
            case 0xD9 -> SendBytecodes.SelfSendNode.create(code, index, 1, code.getSqueakClass().getImage().getSpecialSelector(SPECIAL_OBJECT.SELECTOR_SISTA_TRAP), 1);
            case 0xDA, 0xDB, 0xDC, 0xDD, 0xDE, 0xDF -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 1, b);
            case 0xE0 -> decodeBytecode(frame, code, index, extBytes + 2, (extA << 8) + Byte.toUnsignedInt(bytecode[indexWithExt + 1]), extB, numExtB);
            case 0xE1 -> {
                final int byteValue = Byte.toUnsignedInt(bytecode[indexWithExt + 1]);
                yield decodeBytecode(frame, code, index, extBytes + 2, extA, numExtB == 0 && byteValue > 127 ? byteValue - 256 : (extB << 8) + byteValue, numExtB + 1);
            }
            case 0xE2 -> PushBytecodes.PushReceiverVariableNode.create(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xE3 -> PushBytecodes.PushLiteralVariableNode.create(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xE4 -> new PushBytecodes.PushLiteralConstantNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xE5 -> new PushBytecodes.PushTemporaryLocationNode(code, index, 2, Byte.toUnsignedInt(bytecode[indexWithExt + 1]));
            case 0xE6 -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 1, b);
            case 0xE7 -> PushBytecodes.PushNewArrayNode.create(code, index, 2, bytecode[indexWithExt + 1]);
            case 0xE8 -> new PushBytecodes.PushSmallIntegerNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extB << 8));
            case 0xE9 -> new PushBytecodes.PushCharacterNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xEA -> {
                final int byte1 = Byte.toUnsignedInt(bytecode[indexWithExt + 1]);
                final int literalIndex = (byte1 >> 3) + (extA << 5);
                final int numArgs = (byte1 & 7) + (extB << 3);
                final NativeObject selector = (NativeObject) code.getLiteral(literalIndex);
                yield SendBytecodes.SelfSendNode.create(code, index, 2 + extBytes, selector, numArgs);
            }
            case 0xEB -> {
                boolean isDirected = false;
                int extBValue = extB;
                if (extBValue >= 64) {
                    isDirected = true;
                    extBValue = extBValue & 63;
                }
                final int byte1 = Byte.toUnsignedInt(bytecode[indexWithExt + 1]);
                final int literalIndex = (byte1 >> 3) + (extA << 5);
                final int numArgs = (byte1 & 7) + (extBValue << 3);
                if (isDirected) {
                    yield new SendBytecodes.DirectedSuperSendNode(code, index, 2 + extBytes, literalIndex, numArgs);
                } else {
                    yield new SendBytecodes.SuperSendNode(code, index, 2 + extBytes, literalIndex, numArgs);
                }
            }
            case 0xEC -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 1, b);
            case 0xED -> JumpBytecodes.UnconditionalJumpNode.createLongExtended(code, index, 2 + extBytes, bytecode[indexWithExt + 1], extB);
            case 0xEE -> JumpBytecodes.ConditionalJumpOnTrueNode.createLongExtended(code, index, 2 + extBytes, bytecode[indexWithExt + 1], extB);
            case 0xEF -> JumpBytecodes.ConditionalJumpOnFalseNode.createLongExtended(code, index, 2 + extBytes, bytecode[indexWithExt + 1], extB);
            case 0xF0 -> new StoreBytecodes.PopIntoReceiverVariableNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xF1 -> new StoreBytecodes.PopIntoLiteralVariableNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xF2 -> new StoreBytecodes.PopIntoTemporaryLocationNode(code, index, 2 + extBytes, bytecode[indexWithExt + 1]);
            case 0xF3 -> new StoreBytecodes.StoreIntoReceiverVariableNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xF4 -> new StoreBytecodes.StoreIntoLiteralVariableNode(code, index, 2 + extBytes, Byte.toUnsignedInt(bytecode[indexWithExt + 1]) + (extA << 8));
            case 0xF5 -> new StoreBytecodes.StoreIntoTemporaryLocationNode(code, index, 2 + extBytes, bytecode[indexWithExt + 1]);
            case 0xF6, 0xF7 -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 2, b);
            case 0xF8 -> {
                // TODO: use `final boolean m = bytecode[indexWithExt + 2] >> 8 == 0;`
                // TODO: use `final int s = bytecode[indexWithExt + 2] >> 5 & 3;`
                final int i = Byte.toUnsignedInt(bytecode[indexWithExt + 1]);
                final int j = bytecode[indexWithExt + 2] & 31;
                final int primitiveIndex = i + (j << 8);
                assert 1 <= primitiveIndex && primitiveIndex < 32767 : "primitiveIndex out of range";
                if (primitiveIndex < 1000) {
                    yield new MiscellaneousBytecodes.CallPrimitiveNode(code, index, primitiveIndex);
                }
                yield switch (primitiveIndex) {
                    case 1000 -> new InlinePrimitiveBytecodes.PrimClassNode(code, index);
                    case 1001 -> new InlinePrimitiveBytecodes.PrimNumSlotsNode(code, index);
                    case 1002 -> new InlinePrimitiveBytecodes.PrimBasicSizeNode(code, index);
                    case 1003 -> new InlinePrimitiveBytecodes.PrimNumBytesNode(code, index);
                    case 1004 -> new InlinePrimitiveBytecodes.PrimNumShortsNode(code, index);
                    case 1005 -> new InlinePrimitiveBytecodes.PrimNumWordsNode(code, index);
                    case 1006 -> new InlinePrimitiveBytecodes.PrimNumDoubleWordsNode(code, index);
                    case 1020 -> new InlinePrimitiveBytecodes.PrimIdentityHashNode(code, index);
                    case 1021 -> new InlinePrimitiveBytecodes.PrimIdentityHashSmallIntegerNode(code, index);
                    case 1022 -> new InlinePrimitiveBytecodes.PrimIdentityHashCharacterNode(code, index);
                    case 1023 -> new InlinePrimitiveBytecodes.PrimIdentityHashSmallFloatNode(code, index);
                    case 1024 -> new InlinePrimitiveBytecodes.PrimIdentityHashBehaviorNode(code, index);
                    case 1030 -> new InlinePrimitiveBytecodes.PrimImmediateAsIntegerCharacterNode(code, index);
                    case 1031 -> new InlinePrimitiveBytecodes.PrimImmediateAsIntegerSmallFloatNode(code, index);
                    case 1032 -> new InlinePrimitiveBytecodes.PrimImmediateAsFloatNode(code, index);
                    case 2000 -> new InlinePrimitiveBytecodes.PrimSmallIntegerAddNode(code, index);
                    case 2001 -> new InlinePrimitiveBytecodes.PrimSmallIntegerSubtractNode(code, index);
                    case 2002 -> new InlinePrimitiveBytecodes.PrimSmallIntegerMultiplyNode(code, index);
                    case 2003 -> new InlinePrimitiveBytecodes.PrimSmallIntegerDivideNode(code, index);
                    case 2004 -> new InlinePrimitiveBytecodes.PrimSmallIntegerFloorDivideNode(code, index);
                    case 2005 -> new InlinePrimitiveBytecodes.PrimSmallIntegerFloorModNode(code, index);
                    case 2006 -> new InlinePrimitiveBytecodes.PrimSmallIntegerQuoNode(code, index);
                    case 2016 -> new InlinePrimitiveBytecodes.PrimSmallIntegerBitAndNode(code, index);
                    case 2017 -> new InlinePrimitiveBytecodes.PrimSmallIntegerBitOrNode(code, index);
                    case 2018 -> new InlinePrimitiveBytecodes.PrimSmallIntegerBitXorNode(code, index);
                    case 2019 -> new InlinePrimitiveBytecodes.PrimSmallIntegerBitShiftLeftNode(code, index);
                    case 2020 -> new InlinePrimitiveBytecodes.PrimSmallIntegerBitShiftRightNode(code, index);
                    case 2032 -> new InlinePrimitiveBytecodes.PrimSmallIntegerGreaterThanNode(code, index);
                    case 2033 -> new InlinePrimitiveBytecodes.PrimSmallIntegerLessThanNode(code, index);
                    case 2034 -> new InlinePrimitiveBytecodes.PrimSmallIntegerGreaterOrEqualNode(code, index);
                    case 2035 -> new InlinePrimitiveBytecodes.PrimSmallIntegerLessOrEqualNode(code, index);
                    case 2036 -> new InlinePrimitiveBytecodes.PrimSmallIntegerEqualNode(code, index);
                    case 2037 -> new InlinePrimitiveBytecodes.PrimSmallIntegerNotEqualNode(code, index);
                    case 2065 -> new InlinePrimitiveBytecodes.PrimByteAtNode(code, index);
                    case 2066 -> new InlinePrimitiveBytecodes.PrimShortAtNode(code, index);
                    case 2067 -> new InlinePrimitiveBytecodes.PrimWordAtNode(code, index);
                    case 2068 -> new InlinePrimitiveBytecodes.PrimDoubleWordAtNode(code, index);
                    case 3001 -> new InlinePrimitiveBytecodes.PrimByteAtPutNode(code, index);
                    case 3002 -> new InlinePrimitiveBytecodes.PrimShortAtPutNode(code, index);
                    case 3003 -> new InlinePrimitiveBytecodes.PrimWordAtPutNode(code, index);
                    case 3004 -> new InlinePrimitiveBytecodes.PrimDoubleWordAtPutNode(code, index);
                    case 3021 -> new InlinePrimitiveBytecodes.PrimByteEqualsNode(code, index);
                    case 4000 -> new InlinePrimitiveBytecodes.PrimFillFromToWithNode(code, index);
                    default -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 3, b);
                };
            }
            case 0xF9 -> PushBytecodes.AbstractPushFullClosureNode.createExtended(code, index, 3, extA, bytecode[indexWithExt + 1], bytecode[indexWithExt + 2]);
            case 0xFA -> PushBytecodes.PushClosureNode.createExtended(code, index, 3 + extBytes, extA, extB, bytecode[indexWithExt + 1], bytecode[indexWithExt + 2]);
            case 0xFB -> new PushBytecodes.PushRemoteTempNode(code, index, 3, bytecode[indexWithExt + 1], bytecode[indexWithExt + 2]);
            case 0xFC -> new StoreBytecodes.StoreIntoRemoteTempNode(code, index, 3, bytecode[indexWithExt + 1], bytecode[indexWithExt + 2]);
            case 0xFD -> new StoreBytecodes.PopIntoRemoteTempNode(code, index, 3, bytecode[indexWithExt + 1], bytecode[indexWithExt + 2]);
            case 0xFE, 0xFF -> new MiscellaneousBytecodes.UnknownBytecodeNode(code, index, 3, b);
            default -> throw SqueakException.create("Not a bytecode:", b);
        };
    }

    @Override
    public String decodeToString(final CompiledCodeObject code) {
        CompilerAsserts.neverPartOfCompilation();
        final StringBuilder sb = new StringBuilder();
        final int trailerPosition = trailerPosition(code);
        int bytecodeIndex = 0;
        int lineIndex = 1;
        int indent = 0;
        final byte[] bytes = code.getBytes();
        while (bytecodeIndex < trailerPosition) {
            final int currentByte = Byte.toUnsignedInt(bytes[bytecodeIndex]);
            sb.append(lineIndex);
            for (int j = 0; j < 1 + indent; j++) {
                sb.append(' ');
            }
            final int numBytecodes = decodeNumBytes(code, bytecodeIndex);
            sb.append('<');
            for (int j = bytecodeIndex; j < bytecodeIndex + numBytecodes; j++) {
                if (j > bytecodeIndex) {
                    sb.append(' ');
                }
                if (j < bytes.length) {
                    sb.append(String.format("%02X", bytes[j]));
                }
            }
            sb.append("> ");
            sb.append(decodeBytecodeToString(code, currentByte, bytecodeIndex));
            if (currentByte == 143) {
                indent++; // increment indent on push closure
            } else if (currentByte == 125) {
                indent--; // decrement indent on block return
            }
            lineIndex++;
            bytecodeIndex += numBytecodes;
            if (bytecodeIndex < trailerPosition) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String decodeBytecodeToString(final CompiledCodeObject code, final int b, final int index) {
        final byte[] bytecode = code.getBytes();
        return switch (b) {
            case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> "pushRcvr: " + (b & 15);
            case 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 -> "pushLitVar: " + code.getLiteral(b & 15);
            case 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63 -> "pushConstant: " + code.getLiteral(b & 31);
            case 64, 65, 66, 67, 68, 69, 70, 71 -> "pushTemp: " + (b & 7);
            case 72, 73, 74, 75 -> "pushTemp: " + (b & 3) + 8;
            case 76 -> "self";
            case 77 -> "pushConstant: true";
            case 78 -> "pushConstant: false";
            case 79 -> "pushConstant: nil";
            case 80 -> "pushConstant: 0";
            case 81 -> "pushConstant: 1";
            case 82 -> "pushThisContext:";
            case 83 -> "dup";
            case 84, 85, 86, 87 -> "unknown";
            case 88 -> "returnSelf";
            case 89 -> "return: true";
            case 90 -> "return: false";
            case 91 -> "return: nil";
            case 92 -> "returnTop";
            case 93, 94 -> "blockReturn";
            case 95 -> "nop";
            case 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127 //
                            -> "send: " + code.getSqueakClass().getImage().getSpecialSelector(b - 96).asStringUnsafe();
            case 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175 //
                            -> "send: " + code.getLiteral(b & 0xF);
            case 176, 177, 178, 179, 180, 181, 182, 183 -> "jumpTo: ";
            case 184, 185, 186, 187, 188, 189, 190, 191 -> "jumpTrue: ";
            case 192, 193, 194, 195, 196, 197, 198, 199 -> "jumpFalse: ";
            case 200, 201, 202, 203, 204, 205, 206, 207 -> "popIntoRcvr: " + (b & 7);
            case 208, 209, 210, 211, 212, 213, 214, 215 -> "popIntoTemp: " + (b & 7);
            case 216 -> "pop";
            case 217 -> "send: " + code.getSqueakClass().getImage().getSpecialSelector(SPECIAL_OBJECT.SELECTOR_SISTA_TRAP).asStringUnsafe();
            case 218, 219, 220, 221, 222, 223 -> "unknown";
            case 224 -> "extA";
            case 225 -> "extB";
            case 226 -> "pushRcvr: ";
            case 227 -> "pushLitVar: ";
            case 228 -> "pushConstant: ";
            case 229 -> "pushTemp: ";
            case 230 -> "unknown";
            case 231 -> "push: (Array new: " + (bytecode[index + 1] & 127) + ")";
            case 232 -> "pushConstant: ";
            case 233 -> "pushConstant: $";
            case 234 -> "send: ";
            case 235 -> "sendSuper: ";
            case 236 -> "unknown";
            case 237 -> "jumpTo: ";
            case 238 -> "jumpTrue: ";
            case 239 -> "jumpFalse: ";
            case 240 -> "popIntoRcvr: ";
            case 241 -> "popIntoLit: ";
            case 242 -> "popIntoTemp: ";
            case 243 -> "storeIntoRcvr: ";
            case 244 -> "storeIntoLit: ";
            case 245 -> "storeIntoTemp: ";
            case 246, 247 -> "unknown";
            case 248 -> {
                final int j = bytecode[index + 2] & 31;
                final int primitiveIndex = Byte.toUnsignedInt(bytecode[index + 1]) + j;
                yield "callPrimitive: " + primitiveIndex;
            }
            case 249 -> "pushFullClosure: (self literalAt: ?) numCopied: ? numArgs: ?";
            case 250 -> "closureNumCopied: ? numArgs: ? bytes ? to ?";
            case 251 -> "pushTemp: inVectorAt: ";
            case 252 -> "storeTemp: inVectorAt: ";
            case 253 -> "popTemp: inVectorAt: ";
            case 254, 255 -> "unknown";
            default -> throw SqueakException.create("Unknown bytecode:", b);
        };
    }

    @Override
    public int pcPreviousTo(final CompiledCodeObject code, final int pc) {
        final int initialPC = code.getInitialPC();
        int currentPC = initialPC;
        assert currentPC < pc;
        int previousPC = -1;
        while (currentPC < pc) {
            previousPC = currentPC;
            do {
                currentPC += decodeNumBytes(code, currentPC - initialPC);
            } while (isSistaV1Extension(Byte.toUnsignedInt(code.getBytes()[currentPC - initialPC])));
        }
        assert previousPC > 0;
        return previousPC;
    }

    private static boolean isSistaV1Extension(final int bytecode) {
        return 0xE0 <= bytecode && bytecode <= 0xE1;
    }

    private static int decodeNumBytes(final CompiledCodeObject code, final int index) {
        final int b = Byte.toUnsignedInt(code.getBytes()[index]);
        if (b <= 223) {
            return 1;
        } else if (b <= 247) {
            return 2;
        } else {
            return 3;
        }
    }
}
