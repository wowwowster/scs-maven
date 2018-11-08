package com.sword.springboard;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;


public class CloudSource {

        public final String sourceId;
        public final String sourceName;
        public final String custId;
        public final String filename;


        public CloudSource(final String sourceId, final String sourceName, final String custId, final String filename) {
            super();
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.custId = custId;
            this.filename = filename;
        }
}
