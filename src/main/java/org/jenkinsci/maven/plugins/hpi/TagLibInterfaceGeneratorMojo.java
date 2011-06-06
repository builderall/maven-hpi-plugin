package org.jenkinsci.maven.plugins.hpi;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPackage;
import groovy.lang.Closure;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.kohsuke.stapler.jelly.groovy.TagLibraryUri;
import org.kohsuke.stapler.jelly.groovy.TypedTagLibrary;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Generates the strongly-typed Java interfaces for Groovy taglibs.
 *
 * @author Kohsuke Kawaguchi
 * @goal generate-taglib-interface
 * @phase generate-resources
 */
public class TagLibInterfaceGeneratorMojo extends AbstractMojo {
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory for the generated WAR.
     *
     * @parameter expression="${project.basedir}/target/taglib-interface"
     * @required
     */
    protected File outputDirectory;

    private JCodeModel codeModel;

    private SAXReader saxReader = new SAXReader();

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            codeModel = new JCodeModel();
            for (Resource res: (List<Resource>)project.getBuild().getResources()) {
                walk(new File(res.getDirectory()),codeModel.rootPackage());
            }

            outputDirectory.mkdirs();
            codeModel.build(outputDirectory);
            project.getCompileSourceRoots().add(outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate taglib type interface",e);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException("Duplicate class: "+e.getExistingClass().fullName(),e);
        }
    }

    private void walk(File dir,JPackage pkg) throws JClassAlreadyExistsException, IOException {
        File[] children = dir.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory();
            }
        });
        if (children!=null) {
            for (File child : children)
                walk(child,pkg.subPackage(child.getName()));
        }

        File taglib = new File(dir,"taglib");
        if (taglib.exists()) {
            JDefinedClass c = pkg.parent()._interface(StringUtils.capitalize(dir.getName())+"TagLib");
            c._implements(TypedTagLibrary.class);
            c.annotate(TagLibraryUri.class).param("value",(pkg+"."+dir.getName()).replace('.','/'));

            File[] tags = dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jelly");
                }
            });


            for (File tag : tags) {
                try {
                    Document dom = saxReader.read(tag);
                    Element doc = dom.getRootElement().element(QName.get("st:documentation", "jelly:stapler"));

                    String methodName = FilenameUtils.getBaseName(tag.getName()).replace('-','_');
                    for (int i=0; i<4; i++) {
                        JMethod m = c.method(0, void.class, methodName);
                        if (i%2==0)
                            m.param(Map.class,"args");
                        if ((i/2)%2==0)
                            m.param(Closure.class,"body");

                        JDocComment javadoc = m.javadoc();
                        if (doc!=null)
                            javadoc.append(doc.getText().replace("&","&amp;").replace("<","&lt;"));
                    }
                } catch (DocumentException e) {
                    throw (IOException)new IOException("Failed to parse "+tag).initCause(e);
                }
            }

            // TODO: up to date check

//            File javaFile = new File(output.getParentFile(),dir.getName()+".java");
        }
    }
}