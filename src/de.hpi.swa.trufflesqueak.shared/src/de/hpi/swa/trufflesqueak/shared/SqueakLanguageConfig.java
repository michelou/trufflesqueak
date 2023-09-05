/*
 * Copyright (c) 2017-2023 Software Architecture Group, Hasso Plattner Institute
 * Copyright (c) 2021-2023 Oracle and/or its affiliates
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.shared;

public final class SqueakLanguageConfig {
    public static final String ID = "smalltalk";
    public static final String IMAGE_SOURCE_NAME = "<smalltalk image>";
    public static final String IMPLEMENTATION_NAME = "TruffleSqueak";
    public static final String MIME_TYPE = "application/x-smalltalk";
    public static final String NAME = "Squeak/Smalltalk";
    public static final String ST_MIME_TYPE = "text/x-smalltalk";
    public static final String VERSION = "23.0.1-dev";
    public static final String WEBSITE = "https://github.com/hpi-swa/trufflesqueak";

    public static final String[][] SUPPORTED_IMAGES = {
                    {"TruffleSqueak image (23.0.1) (recommended)", "https://github.com/hpi-swa/trufflesqueak/releases/download/23.0.1/TruffleSqueakImage-23.0.1.zip"},
                    {"Squeak/Smalltalk (6.0-22104)", "https://files.squeak.org/6.0/Squeak6.0-22104-64bit/Squeak6.0-22104-64bit.zip"},
                    {"TruffleSqueak test image (6.0alpha-20228b)", "https://github.com/hpi-swa/trufflesqueak/releases/download/21.1.0/TruffleSqueakTestImage-6.0alpha-20228b-64bit.zip"},
                    {"Cuis-Smalltalk test image (6.0-5053)", "https://github.com/hpi-swa/trufflesqueak/releases/download/21.3.0/CuisTestImage-6.0-5053.zip"}};

    private SqueakLanguageConfig() {
    }
}
