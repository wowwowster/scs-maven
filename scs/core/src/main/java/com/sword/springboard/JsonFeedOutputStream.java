//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.sword.springboard;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.api.client.testing.json.MockJsonGenerator;
import com.google.api.services.springboardindex.Springboardindex;
import com.google.enterprise.springboard.common.Application;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.log4j.Logger;
import sword.common.utils.StringUtils;
import sword.common.utils.streams.DoNotCloseUnderlyingOutputStream;
import sword.common.utils.streams.StreamUtils;
import sword.gsa.xmlfeeds.builder.Action;
import sword.gsa.xmlfeeds.builder.Authmethod;
import sword.gsa.xmlfeeds.builder.FeedType;
import sword.gsa.xmlfeeds.builder.Metadata;
import sword.gsa.xmlfeeds.builder.OutputMode;
import sword.gsa.xmlfeeds.builder.Scoring;
import sword.gsa.xmlfeeds.builder.acl.ACL;
import sword.gsa.xmlfeeds.builder.streamed.Document;
import sword.gsa.xmlfeeds.builder.streamed.IOExceptionWrapper;
import sword.gsa.xmlfeeds.builder.streamed.RateWatcher;
import sword.gsa.xmlfeeds.builder.streamed.SendFeed;
import sword.gsa.xmlfeeds.builder.streamed.XMLOutputStream;

public class JsonFeedOutputStream implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(JsonFeedOutputStream.class);
    private static final int BUF_SIZE = 16384;
    private long lastContentStart;
    final Charset cs;
    private long lastRecordEnd;
    private RateWatcher rw = null;
    private FileOutputStream fos;
    private JsonGenerator jg;
    private final String fileBaseName;
    File currentJson;
    protected long lastRecordStart;
    protected int itemsAdded;
    private static final Random RND = new Random();
    private final File feedsFolder;
    final long jsonFeedMaxSize;
    private final boolean canHaveContent;

    public JsonFeedOutputStream(String gsaHost, String datasourceName, FeedType feedType, File feedsFolder, Charset jsonCharset, long jsonFeedMaxSize) throws IOException {
        this.feedsFolder = feedsFolder;
        this.fileBaseName = "feed";
        this.cs = jsonCharset == null? StandardCharsets.UTF_8:jsonCharset;
        this.jsonFeedMaxSize = jsonFeedMaxSize;
        this.jg = new JsonFactory().createGenerator(fos, JsonEncoding.UTF8);
        jg.setPrettyPrinter(new DefaultPrettyPrinter());
        this.canHaveContent = feedType != FeedType.WEB;
        this.init();
    }

    protected void init() throws IOException {
        this.currentJson = this.newJson();
        this.fos = new FileOutputStream(this.currentJson);
        this.lastRecordStart = -1L;
        this.itemsAdded = 0;
        jg.writeStartObject();
            jg.writeStringField("isIncremental","true");
            jg.writeArrayFieldStart("requests");
    }
    File newJson() throws IOException {
        Random var1 = RND;
        synchronized(RND) {
            if(!this.feedsFolder.exists()) {
                this.feedsFolder.mkdirs();
            }

            return new File(this.feedsFolder, String.format("%s_%s_%s_%s.xml", new Object[]{this.fileBaseName, Long.toHexString(Thread.currentThread().getId()), Long.toHexString(System.currentTimeMillis()), Integer.toHexString(RND.nextInt(512))}));
        }
    }

    public void close() {
        if(this.fos != null) {
            try {
                this.fos.close();
            } catch (Exception var2) {
                ;
            }

            if(!this.containsAnyRecord() && !this.currentJson.delete()) {
                this.currentJson.deleteOnExit();
            }
        }

    }

    public void removeLastRecord() throws IOException {
        this.fos.getChannel().truncate(this.lastRecordStart);
        --this.itemsAdded;
    }

    public boolean containsAnyRecord() {
        return this.itemsAdded > 0;
    }

    public SendFeed closeAndPrepareShipment() throws IOException {
        return this.closeAndPrepareShipment(true, -1L);
    }

    public SendFeed closeAndPrepareShipment(boolean reInit, long lastFileAddedLength) throws IOException {
        jg.writeEndArray();
        jg.writeEndObject();
        this.fos.flush();
        this.fos.close();
        SendFeed sf = new SendFeed(this.currentJson, lastFileAddedLength);
        if(reInit) {
            this.init();
        }

        return sf;
    }

    public void setRateWatcher(RateWatcher rw) {
        this.rw = rw;
    }

    public void addGroupACL(ACL acl) throws IOException, SendFeed {
        this.notifyNewRecord();
        //acl.toJson(jg,2, false);
        this.lastRecordEnd = this.fos.getChannel().position();
        if(this.lastRecordEnd > this.jsonFeedMaxSize) {
            throw this.transferLastRecordToNewFile((Document)null, false, -1L);
        }
    }

    void notifyNewRecord() throws IOException {
        ++this.itemsAdded;
        this.lastRecordStart = this.fos.getChannel().position();
    }

    public void addDeleteRecord(String url) throws SendFeed, IOException {
        this.notifyNewRecord();
        jg.writeStartObject();
        //TODO what is inside a SB delete
        jg.writeEndObject();
        this.lastRecordEnd = this.fos.getChannel().position();
    }

    public long addWebRecord(String url, String displayurl, boolean lock, Authmethod authmethod, boolean crawlImmediately, boolean crawlOnce, Scoring scoring, ACL acl, Date lastModificationDate, List<Metadata> metadata) throws SendFeed, IOException {
        return this.addRecord(url, displayurl, lock, authmethod, -1, crawlImmediately, crawlOnce, scoring, acl, Document.webDocument(lastModificationDate, metadata), -1L);
    }

    public long addContentRecord(String url, String displayurl, boolean lock, Authmethod authmethod, int pagerank, ACL acl, Document document, long maxContentSize) throws SendFeed, IOException {
        return this.addRecord(url, displayurl, lock, authmethod, pagerank, false, false, (Scoring)null, acl, document, maxContentSize);
    }


    public long addRecord(String url, String displayurl, boolean lock, Authmethod authmethod, int pagerank, boolean crawlImmediately, boolean crawlOnce, Scoring scoring, ACL acl, Document document, long maxContentSize) throws SendFeed, IOException {
        Date lastModified = document.lastModificationDate;
        long total = 0L;
        if(lastModified == null) {
            lastModified = new Date();
        }
        this.notifyNewRecord();
        String contentId = "URL content"; //TODO hardcoded value
        if(authmethod != null && !StringUtils.isNullOrEmpty(url)) {
            jg.writeStartObject();
                jg.writeStringField("type","items.update");
                jg.writeObjectFieldStart("item");
                    jg.writeStringField("id",contentId);
                    jg.writeStringField("container","Folder");
                    jg.writeStringField("mimeType","text/html");
                    jg.writeArrayFieldStart("readers");
                     //   acl.toJson(jg);
                    jg.writeEndArray();
                    jg.writeStringField("viewUrl",displayurl);
                jg.writeEndObject();
            if(this.canHaveContent) {
                jg.writeObjectFieldStart("content");
                jg.writeStringField("type",document.mime);
                switch(contentId){
                    case "URL content":jg.writeStringField("url", displayurl);
                    case "Indexed document":
                      //  String content = Document.toHTML(document, this.cs);
                       // jg.writeStringField("content", content);
                   // total = content.length();
                }

            } else {
                this.lastRecordEnd = this.fos.getChannel().position();
                return -1L;
            }

                jg.writeEndObject();
            jg.writeEndObject();
        } else {
            throw new IllegalArgumentException("Missing mandatory parameter");
        }
        if(this.rw != null) {
            this.rw.updateCount(total);
        }
        this.lastRecordEnd = this.fos.getChannel().position();
        if(this.lastRecordEnd > this.jsonFeedMaxSize) {
            throw this.transferLastRecordToNewFile(document, true, total);
        } else {
            return total;
        }
    }


    private SendFeed transferLastRecordToNewFile(Document document, boolean hasContent, long lastFileAddedLength) throws IOException {
        File newJson = this.newJson();
        FileOutputStream newFos = new FileOutputStream(newJson);
        JsonGenerator newJg = new JsonFactory().createGenerator(fos, JsonEncoding.UTF8);
        newJg.writeStartObject();
        newJg.writeStringField("isIncremental","true");
        newJg.writeArrayFieldStart("requests");
        FileChannel newFosCh = newFos.getChannel();
        long newLRS = newFosCh.position();
        long newLRE = -1L;

        SendFeed b64os2;
        try {
            boolean removedContent = false;
            if(hasContent && this.lastRecordEnd - this.lastRecordStart > this.jsonFeedMaxSize) {
                LOG.warn("Last record is too large; content will be removed");
                this.lastRecordEnd = this.lastContentStart;
                removedContent = true;
            }

            FileInputStream b64os = new FileInputStream(this.currentJson);
            Throwable var14 = null;

            try {
                StreamUtils.channelTransfer(b64os.getChannel(), newFosCh, 1048576L, this.lastRecordStart, this.lastRecordEnd);
            } catch (Throwable var50) {
                var14 = var50;
                throw var50;
            } finally {
                if(b64os != null) {
                    if(var14 != null) {
                        try {
                            b64os.close();
                        } catch (Throwable var47) {
                            var14.addSuppressed(var47);
                        }
                    } else {
                        b64os.close();
                    }
                }

            }

            if(removedContent) {
                if(document != null) {
                    Base64OutputStream b64os1 = new Base64OutputStream(new DoNotCloseUnderlyingOutputStream(newFos, 16384));
                    var14 = null;

                    try {
                        byte[] x2 = Document.toHTML(document, this.cs);
                        b64os1.write(x2);
                    } catch (Throwable var49) {
                        var14 = var49;
                        throw var49;
                    } finally {
                        if(b64os1 != null) {
                            if(var14 != null) {
                                try {
                                    b64os1.close();
                                } catch (Throwable var48) {
                                    var14.addSuppressed(var48);
                                }
                            } else {
                                b64os1.close();
                            }
                        }

                    }
                }

            }

            newLRE = newFosCh.position();
            this.fos.getChannel().truncate(this.lastRecordStart);
            b64os2 = this.closeAndPrepareShipment(false, lastFileAddedLength);
        } finally {
            this.currentJson = newJson;
            this.fos = newFos;
            this.lastRecordStart = newLRS;
            this.lastRecordEnd = newLRE;
            this.itemsAdded = 1;
        }

        return b64os2;
    }
}
