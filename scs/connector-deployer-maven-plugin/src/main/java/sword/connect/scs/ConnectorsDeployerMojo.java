package sword.connect.scs;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Says "Hi" to the user.
 *
 */
@Mojo( name = "deployconnectors")
public class ConnectorsDeployerMojo extends AbstractMojo
{
    @Parameter( property = "deployconnectors.scshome", defaultValue = "NONE" )
    private String scshome;

    public void execute() throws MojoExecutionException
    {
        if (scshome!=null && !"NONE".equals(scshome)) {

            System.out.println("scshome: " + scshome);
        }
        else{
            throw new MojoExecutionException("Error scshome property is not defined. Please add it to your plugin configuration in pom.xml");
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setIncludes(new String[]{"**/connector-*/target/*.jar"});
        scanner.setExcludes(new String[]{"**/connector-deployer-maven-plugin/**"});
        scanner.setBasedir(scshome);
        scanner.setCaseSensitive(false);
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        System.out.println("Adding connector files: ");
        for ( int i = 0; i < files.length; i++ )
        {
            System.out.println(files[i]);
            String[] path=files[i].split("\\\\");
            String connectorName= path[0];
            String jarName= path[path.length-1];
            System.out.println("Connector name: "+ connectorName+" Jar name: "+jarName );

            try {
                File classesSource=new File(scshome+"\\"+connectorName+"\\target\\classes");
                File classesDest=new File(scshome+"\\tomcat/scs/connectors/"+connectorName+"/classes");
                File libSource=new File(scshome+"\\"+connectorName+"\\target\\lib");
                File libDest=new File(scshome+"\\tomcat/scs/connectors/"+connectorName+"/lib");
                System.out.println("Copying file from: "+classesSource.getAbsolutePath()+" to: "+classesDest.getAbsolutePath());
                FileUtils.copyDirectoryStructure(classesSource,classesDest);
                FileUtils.copyFile(new File(classesSource+"/log4j.properties"),new File(classesDest+"/../log4j.properties"));
                FileUtils.fileDelete(classesDest.getAbsolutePath()+"/log4j.properties");
                System.out.println("Copying file from: "+libSource.getAbsolutePath()+" to: "+libDest.getAbsolutePath());
                FileUtils.copyDirectoryStructure(libSource,libDest);
            } catch (IOException e) {
                System.out.println("Error copying file for connector "+connectorName);

            }
        }
    }



}
