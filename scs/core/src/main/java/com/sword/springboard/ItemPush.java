package com.sword.springboard;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.api.client.util.Base64;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.StringUtils;
import com.google.api.services.springboardindex.model.*;
import com.google.api.services.springboardindex.model.Principal;
import org.apache.axiom.om.ds.ByteArrayDataSource;
import org.apache.log4j.Logger;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.springboardindex.Springboardindex;
import com.google.enterprise.springboard.common.Application;

import org.apache.commons.io.*;
import sword.gsa.xmlfeeds.builder.Metadata;
import sword.gsa.xmlfeeds.builder.acl.*;
import sword.gsa.xmlfeeds.builder.streamed.Document;

public final class ItemPush extends Application {

    private static final Logger LOG = Logger.getLogger(ItemPush.class);


    private ArrayList<String> idToDelete;
    private HashMap<String, byte[]> docMapper;



    public ItemPush(Options options) throws IOException, GeneralSecurityException {
        super(options);
        idToDelete = new ArrayList<String>();
        docMapper = new HashMap<String,byte[]>();
    }

    public void authenticateWithSB() throws IOException {
        System.out.println(service.items().list(customerId, sourceId).buildHttpRequest().getUrl());
        ListItemsResponse response = service.items().list(customerId, sourceId)
                .setBrief(false)
                .execute();
        if(response != null && response.getItems() != null)
        for (Item item : response.getItems()) {
            System.out.println(item.toPrettyString());
        }
    }

    public void mapUsers() {

    }

    public void createContainers() {
        //Does it always make sense to create a container per user?
        //Possibly, if we want to delete all items by a user, it makes it easier
    }

    public void createRootContainer() throws IOException {
        Item rootFolder = new Item().setId("root").setReaders(Arrays.asList(new Principal().setKind("customer")));
        Springboardindex.Items.Update update = service.items()
                .update(customerId, sourceId, rootFolder.getId(), rootFolder);
        update.setIsIncremental(true);
        update.execute();
    }

    public void clearAllItems() {
        idToDelete.clear();
        docMapper.clear();
    }

    /**
     * This is the main entry point
     * @param document
     * @throws UnsupportedEncodingException
     */
    public Item registerSBItem(List<Item> itemsToPush, Document document, String displayUrl, ACL acl) throws IOException {

        LOG.debug("Request to register SB item received for " + displayUrl);
        List<Metadata> metadata = document.metadata;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        docMapper.put(document.id, IOUtils.toByteArray(document.content));

        Item item = new Item()
               // .setCreatedTime(new DateTime(document.lastModificationDate))
                .setId(document.id)
                //.setTitle(document.title)
                .setContentModifiedTime(new DateTime(document.lastModificationDate))
                .setMetadataModifiedTime(new DateTime(document.lastModificationDate))
               // .encodeContentHash(docMapper.get(document.id))
             //   .encodeMetadataHash(Arrays.toString(document.metadata.toArray()).getBytes("UTF8"))
                .setViewUrl(displayUrl)
                .setMimeType(document.mime);
               // .setContainer("root")
               // .setInheritFrom("root")
               // .setInheritanceType("parent_override");
        // .setReaders(Arrays.asList(new Principal().setKind("customer"))); //ACLS HERE!!!!!!!!
                if(acl!=null){
                    if(sword.common.utils.StringUtils.isNullOrEmpty(acl.inheritFrom))item.setContainer("root");
                    else  item.setContainer(acl.inheritFrom)
                            .setInheritFrom(acl.inheritFrom);
                   if(acl.inheritanceType.toString().toLowerCase() == "child_override" || acl.inheritanceType.toString().toLowerCase() == "parent_override" || acl.inheritanceType.toString().toLowerCase() == "and_both_permit")
                            item
                                    .setInheritanceType(acl.inheritanceType.toString().toLowerCase())
                            .setReaders(convertPrincipals(acl.principals));
                }else{
                            item
                                    .setContainer("root")
                                    .setReaders(Arrays.asList(new Principal().setKind("customer")));
                }

                for (Metadata meta : metadata){
                    item.set(meta.name,meta.value);
                }
        itemsToPush.add(item);

        return item;
    }

    public ExternalGroup registerSBGroup(List<ExternalGroup> groupsToPush, Group scsGroup){
        ArrayList<Principal> members = new ArrayList<Principal>();
        for (sword.gsa.xmlfeeds.builder.acl.Principal member : scsGroup.getMembers()){
            members.add(new Principal().setKind("external#user").setId(member.namespace+"\\"+member.principal));
        }
        ExternalGroup group = new ExternalGroup().setId(scsGroup.principal).setMembers(members);

        groupsToPush.add(group);

        return group;
    }

    public ExternalUser registerSBUser(List<ExternalUser> usersToPush, User u){
        ExternalUser user = new ExternalUser().setId(u.namespace+"\\"+u.principal);

        usersToPush.add(user);

        return user;
    }

    public Item registerSBItem(List<Item> itemsToPush, Document document, String displayUrl) throws IOException {
        return registerSBItem(itemsToPush, document, displayUrl, null);
    }

    public boolean deleteListIsEmpty(){
        return idToDelete.isEmpty();
    }

    private List<Principal> convertPrincipals(List<sword.gsa.xmlfeeds.builder.acl.Principal> aclPrincipalList){
        ArrayList<Principal> newList = new ArrayList<Principal>();
        for (sword.gsa.xmlfeeds.builder.acl.Principal p : aclPrincipalList){
            newList.add(new Principal().setId(p.namespace+"\\"+p.principal).setKind(""));
        }
        return newList;
    }
    public void pushSingleItem(Item document) throws IOException {
        byte[] content = docMapper.get(document.getId());
     //   System.out.println(service.items().update(customerId, sourceId, document.getId(), document,new ByteArrayContent(document.getMimeType(),IOUtils.toByteArray(docMapper.get(document.getId())))).getHttpContent().getLength());
        Springboardindex.Items.Update update = service.items()
                .update(customerId, sourceId, document.getId(), document,new ByteArrayContent(document.getMimeType(),content));
        //getcontent from
       // System.out.println(new String(document.decodeContentHash(), StandardCharsets.UTF_8));
        LOG.info("pushing "+document.getId());
        //update.setIsIncremental(true);
        update.getMediaHttpUploader().setDirectUploadEnabled(true);
        update.execute();
    }

    public void deleteSingleItem(String docId) throws IOException {
        Springboardindex.Items.Delete delete = service.items().delete(customerId, sourceId, docId);
        delete.execute();
    }

    public void deleteAll() throws IOException {
        for (String id : idToDelete){
            deleteSingleItem(id);
        }
    }

    public void pushSingleGroup(ExternalGroup group) throws IOException {

        service.externalgroups().update(customerId, sourceId, group.getId(), group)
                .execute();
    }

    public void pushSingleUser(ExternalUser user) throws IOException {

        service.externalusers().update(customerId, sourceId, user.getId(), user)
                .execute();
    }

    public void deleteSingleGroup(String groupId) throws IOException {
        Springboardindex.Externalgroups.Delete delete = service.externalgroups().delete(customerId, sourceId, groupId);
        delete.execute();
    }

    public void deleteSingleUser(String userId) throws IOException {
        Springboardindex.Externalgroups.Delete delete = service.externalgroups().delete(customerId, sourceId, userId);
        delete.execute();
    }

     public void pushAllItems(List<Item> itemsToPush) throws IOException {
         for(Item i : itemsToPush) {
             try {
                 pushSingleItem(i);
                 LOG.info("successfully sent "+i.toPrettyString());
             } catch (IOException e) {
                 LOG.info("error while sending "+i.toPrettyString());
                 e.printStackTrace();
             }
         }
         itemsToPush.clear();
     }

    public void pushAllGroups(List<ExternalGroup> groupsToPush) throws IOException {
        for(ExternalGroup g : groupsToPush) {
            try {
                pushSingleGroup(g);
                LOG.info("successfully sent "+g.toPrettyString());
            } catch (IOException e) {
                LOG.info("error while sending "+g.toPrettyString());
                e.printStackTrace();
            }
        }
        groupsToPush.clear();
    }

    public void pushAllUsers(List<ExternalUser> usersToPush) throws IOException {
        for(ExternalUser u : usersToPush) {
            try {
                pushSingleUser(u);
                LOG.info("successfully sent "+u.toPrettyString());
            } catch (IOException e) {
                LOG.info("error while sending "+u.toPrettyString());
                e.printStackTrace();
            }
        }
        usersToPush.clear();
    }

    public void addToDelete(String id){
        idToDelete.add(id);
    }
    /**
     * Need to set the values for the options from the GUI
     * @param args
     */
    public static void main(String[] args) {
        // Resolve the arguments relative to the current directory.
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path propsPath = userDir.resolve("");

        Options options = new Options().setConfigPath(propsPath).setHttpRequestInitializer(new RetryRequestInitializer());
        try {
            new ItemPush(options);
        } catch (IOException | GeneralSecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


}