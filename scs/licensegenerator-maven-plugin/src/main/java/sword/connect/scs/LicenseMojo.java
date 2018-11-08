package sword.connect.scs;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.sword.scs.utils.LicMgr;
import com.sword.scs.utils.LicenseGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.prefs.BackingStoreException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


@Mojo( name = "generatelicense")
public class LicenseMojo
    extends AbstractMojo
{
    @Parameter( property = "generatelicense.webxmllocation", defaultValue = "NONE" )
    private String webxmllocation;

    public void execute()
        throws MojoExecutionException
    {
        if (webxmllocation!=null && !"NONE".equals(webxmllocation)){
            try {
                System.out.println("webxmllocation: "+webxmllocation);
                LicenseGenerator licGen = new LicenseGenerator();
                String instanceID = LicMgr.getInstanceID();
                System.out.println("instanceID: "+instanceID);
                String license=licGen.generateLicenseString(instanceID,"20441231");
                System.out.println("generated Instance: "+license);
                System.out.println("Modifying web.xml: ");
                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                Document doc = docBuilder.parse(new File(webxmllocation+"/web_default.xml"));
                Node root = doc.getFirstChild();
                NodeList contextParams = doc.getElementsByTagName("context-param");

                for (int i=0;i<contextParams.getLength();i++){
                    Element contextParam= (Element) contextParams.item(i);
                    if("License".equals(contextParam.getElementsByTagName("param-name").item(0).getFirstChild().getNodeValue())){
                        contextParam.getElementsByTagName("param-value").item(0).setTextContent(license);
                    }
                }

                //write the updated document to file or console
                doc.getDocumentElement().normalize();
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(new File(webxmllocation+"/web.xml"));
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(source, result);
                System.out.println("Web XML file updated successfully");

            } catch (BackingStoreException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            } catch (TransformerConfigurationException e) {
                e.printStackTrace();
            } catch (TransformerException e) {
                e.printStackTrace();
            }
        }
        else{
            throw new MojoExecutionException("Error webxmllocation property is not defined. Please add it to your plugin configuration in pom.xml");
        }

    }
}
