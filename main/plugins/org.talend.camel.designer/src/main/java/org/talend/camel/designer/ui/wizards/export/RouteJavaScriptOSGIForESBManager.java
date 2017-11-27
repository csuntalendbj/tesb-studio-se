// ============================================================================
//
// Copyright (C) 2006-2017 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.camel.designer.ui.wizards.export;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.IPath;
import org.talend.camel.core.model.camelProperties.CamelProcessItem;
import org.talend.core.GlobalServiceRegister;
import org.talend.core.model.process.ElementParameterParser;
import org.talend.core.model.process.IProcess;
import org.talend.core.model.properties.ProcessItem;
import org.talend.core.repository.constants.FileConstants;
import org.talend.core.runtime.process.ITalendProcessJavaProject;
import org.talend.designer.camel.dependencies.core.DependenciesResolver;
import org.talend.designer.camel.resource.core.model.ResourceDependencyModel;
import org.talend.designer.camel.resource.core.util.RouteResourceUtil;
import org.talend.designer.core.model.utils.emf.talendfile.ConnectionType;
import org.talend.designer.core.model.utils.emf.talendfile.NodeType;
import org.talend.designer.runprocess.IProcessor;
import org.talend.designer.runprocess.IRunProcessService;
import org.talend.repository.documentation.ExportFileResource;
import org.talend.repository.ui.wizards.exportjob.scriptsmanager.esb.DataSourceConfig;
import org.talend.repository.utils.EmfModelUtils;
import org.talend.repository.utils.TemplateProcessor;
import org.talend.utils.io.FilesUtils;

import aQute.bnd.osgi.Analyzer;

/**
 * DOC ycbai class global comment. Detailled comment
 */
public class RouteJavaScriptOSGIForESBManager extends AdaptedJobJavaScriptOSGIForESBManager {

    private final Collection<String> routelets;

    public RouteJavaScriptOSGIForESBManager(Map<ExportChoice, Object> exportChoiceMap, String contextName,
        Collection<String> routelets) {
        super(exportChoiceMap, contextName, null, IProcessor.NO_STATISTICS, IProcessor.NO_TRACES);
        this.routelets = routelets;
    }

    @Override
    protected ExportFileResource getCompiledLibExportFileResource(ExportFileResource[] processes) {
        Pattern p = Pattern.compile("([\\s\\S]*)camel-core-\\d+(.\\d+)*(\\S+)*(\\.jar)$");

        ExportFileResource libResource = new ExportFileResource(null, LIBRARY_FOLDER_NAME);
        // Gets talend libraries
        List<URL> talendLibraries = getExternalLibraries(true, processes, getCompiledModuleNames());
        if (talendLibraries != null) {

            for (int i = 0; i < talendLibraries.size(); i++) {
                URL tURL = talendLibraries.get(i);
                if (p.matcher(tURL.getFile()).matches()) {
                    continue;
                } else {
                    libResource.addResources(talendLibraries);
                }
            }

        }
        addRoutinesResources(processes, libResource);
        return libResource;
    }

    /**
     * DOC yyan RouteJavaScriptOSGIForESBManager constructor comment.
     * @param exportChoice
     * @param context
     * @param routelets2
     * @param statisticsPort
     * @param tracePort
     */
    public RouteJavaScriptOSGIForESBManager(Map<ExportChoice, Object> exportChoiceMap, String context,
            Collection<String> routelets, int statisticsPort, int tracePort) {
        super(exportChoiceMap, context, null, statisticsPort, tracePort);
        this.routelets = routelets;
    }

    public static String getClassName(ProcessItem processItem) {
        return getPackageName(processItem) + PACKAGE_SEPARATOR + processItem.getProperty().getLabel();
    }

    protected String getIncludeRoutinesPath() {
        return USER_BEANS_PATH;
    }

    protected Collection<String> getRoutinesPaths() {
        final Collection<String> include = new ArrayList<String>();
        include.add(getIncludeRoutinesPath());
        include.add(SYSTEM_ROUTINES_PATH);
        return include;
    }

    // Add Route Resource http://jira.talendforge.org/browse/TESB-6227
    @Override
    protected void addResources(ExportFileResource osgiResource, ProcessItem processItem) throws Exception {
        IFolder srcFolder = null;
        if (GlobalServiceRegister.getDefault().isServiceRegistered(IRunProcessService.class)) {
            IRunProcessService processService = (IRunProcessService) GlobalServiceRegister.getDefault().getService(
                    IRunProcessService.class);
            ITalendProcessJavaProject talendProcessJavaProject = processService.getTalendProcessJavaProject();
            if (talendProcessJavaProject != null) {
                srcFolder = talendProcessJavaProject.getResourcesFolder();
            }
        }
        if (srcFolder == null) {
            return;
        }
        IPath srcPath = srcFolder.getLocation();

        // http://jira.talendforge.org/browse/TESB-6437
        // https://jira.talendforge.org/browse/TESB-7893
        for (IPath path : RouteResourceUtil.synchronizeRouteResource(processItem)) {
            osgiResource.addResource(path.removeLastSegments(1).makeRelativeTo(srcPath).toString(), path.toFile().toURI()
                    .toURL());
        }
    }

    private static final String TEMPLATE_BLUEPRINT_ROUTE = "/resources/blueprint-template.xml"; //$NON-NLS-1$

    private void handleSpringXml(File springTargetFile, ProcessItem processItem, InputStream springContentInputStream,
            ExportFileResource osgiResource, boolean isRootXml) {

        Element root = null;
        try {
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(springContentInputStream);

            root = document.getRootElement();

            if (isRootXml) {
                root.addNamespace("bp", "http://www.osgi.org/xmlns/blueprint/v1.0.0");
                root.setName("bp:blueprint");

                Attribute schemaLocation = root.attribute("schemaLocation");
                String value = schemaLocation.getValue()
                        + "            http://www.osgi.org/xmlns/blueprint/v1.0.0 http://www.osgi.org/xmlns/blueprint/v1.0.0/blueprint.xsd";

                schemaLocation.setValue(value);
            }

            List<Element> imports = root.elements("import");

            for (Element ip : imports) {
                Attribute resource = ip.attribute("resource");

                URL path = new URL(null, resource.getValue(), new URLStreamHandler() {

                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        return null;
                    }

                });
                for (ResourceDependencyModel resourceModel : RouteResourceUtil.getResourceDependencies(processItem)) {
                    if (path.getPath().equals(path.getPath().startsWith("/") ? "/" + resourceModel.getClassPathUrl()
                            : resourceModel.getClassPathUrl())) {
                        IFile resourceFile = RouteResourceUtil.getSourceFile(resourceModel.getItem());
                        final File tf = new File(getTmpFolder() + PATH_SEPARATOR + resourceModel.getClassPathUrl());
                        handleSpringXml(tf, processItem, resourceFile.getContents(), osgiResource, false);
                        resource.setValue(".." + File.separator + ".." + File.separator + resourceModel.getClassPathUrl());
                    }
                }
            }

            InputStream inputStream = new ByteArrayInputStream(root.asXML().getBytes());

            FilesUtils.copyFile(inputStream, springTargetFile);

            if (isRootXml) {
                osgiResource.addResource(FileConstants.BLUEPRINT_FOLDER_NAME, springTargetFile.toURI().toURL());
            } else {
                osgiResource.addResource("", springTargetFile.toURI().toURL());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                springContentInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void generateConfig(ExportFileResource osgiResource, ProcessItem processItem, IProcess process) throws IOException {
        final File targetFile = new File(getTmpFolder() + PATH_SEPARATOR + "blueprint.xml"); //$NON-NLS-1$

        Map<String, Object> collectRouteInfo = collectRouteInfo(processItem, process);

        TemplateProcessor.processTemplate("ROUTE_BLUEPRINT_CONFIG", //$NON-NLS-1$
                collectRouteInfo, targetFile, getClass().getResourceAsStream(TEMPLATE_BLUEPRINT_ROUTE));

        final File springTargetFile = new File(
                getTmpFolder() + PATH_SEPARATOR + collectRouteInfo.get("name").toString().toLowerCase() + ".xml"); //$NON-NLS-1$

        osgiResource.addResource(FileConstants.BLUEPRINT_FOLDER_NAME, targetFile.toURI().toURL());

        String springContent = "";
        if (processItem instanceof CamelProcessItem) {
            springContent = ((CamelProcessItem) processItem).getSpringContent();
        }

        InputStream springContentInputStream = new ByteArrayInputStream(springContent.getBytes());

        handleSpringXml(springTargetFile, processItem, springContentInputStream, osgiResource, true);
    }

    private Map<String, Object> collectRouteInfo(ProcessItem processItem, IProcess process) {
        Map<String, Object> routeInfo = new HashMap<String, Object>();

        // route name and class name
        routeInfo.put("name", processItem.getProperty().getLabel()); //$NON-NLS-1$
        String className = getClassName(processItem);
        String idName = className;
        String suffix = getOsgiServiceIdSuffix();
        if (suffix != null && suffix.length() > 0) {
        	idName += suffix;
        }
        routeInfo.put("className", className); //$NON-NLS-1$
        routeInfo.put("idName", idName); //$NON-NLS-2$

        boolean useSAM = false;
        boolean useSL = false;
        boolean hasCXFUsernameToken = false;
        boolean hasCXFSamlConsumer = false;
        boolean hasCXFSamlProvider = false;
        boolean hasCXFRSSamlProviderAuthz = false;

        Collection<NodeType> cSOAPs = EmfModelUtils.getComponentsByName(processItem, "cSOAP");
        boolean hasCXFComponent = !cSOAPs.isEmpty();
        cSOAPs.addAll(EmfModelUtils.getComponentsByName(processItem, "cREST"));
        if (!cSOAPs.isEmpty()) {
            Set<String> consumerNodes = new HashSet<String>();
            @SuppressWarnings("unchecked")
            List<ConnectionType> connections = processItem.getProcess().getConnection();
            for (ConnectionType conn : connections) {
                consumerNodes.add(conn.getTarget());
            }

            boolean isEEVersion = isStudioEEVersion();
            for (NodeType node : cSOAPs) {
                boolean nodeUseSAM = false;
                boolean nodeUseSaml = false;
                boolean nodeUseAuthz = false;
                boolean nodeUseRegistry = false;

                // http://jira.talendforge.org/browse/TESB-3850
                String format = EmfModelUtils.computeTextElementValue("DATAFORMAT", node); //$NON-NLS-1$

                if (!useSAM && !"RAW".equals(format)) { //$NON-NLS-1$
                    nodeUseSAM = EmfModelUtils.computeCheckElementValue("ENABLE_SAM", node) //$NON-NLS-1$
                            || EmfModelUtils.computeCheckElementValue("SERVICE_ACTIVITY_MONITOR", node); //$NON-NLS-1$
                }

                // security is disable in case CXF_MESSAGE or RAW dataFormat
                if (!"CXF_MESSAGE".equals(format) && !"RAW".equals(format)) { //$NON-NLS-1$  //$NON-NLS-2$
                    if (isEEVersion && EmfModelUtils.computeCheckElementValue("ENABLE_REGISTRY", node)) { //$NON-NLS-1$
                        nodeUseRegistry = true;
                        // https://jira.talendforge.org/browse/TESB-10725
                        nodeUseSAM = false;
                    } else if (EmfModelUtils.computeCheckElementValue("ENABLE_SECURITY", node)) { //$NON-NLS-1$
                        String securityType = EmfModelUtils.computeTextElementValue("SECURITY_TYPE", node); //$NON-NLS-1$
                        if ("USER".equals(securityType)) { //$NON-NLS-1$
                            hasCXFUsernameToken = true;
                        } else if ("SAML".equals(securityType)) { //$NON-NLS-1$
                            nodeUseSaml = true;
                            nodeUseAuthz = isEEVersion && EmfModelUtils.computeCheckElementValue("USE_AUTHORIZATION", node);
                        }
                    }
                }
                useSAM |= nodeUseSAM;

                useSL = EmfModelUtils.computeCheckElementValue("ENABLE_SL", node) //$NON-NLS-1$
                        || EmfModelUtils.computeCheckElementValue("SERVICE_LOCATOR", node);

                if (consumerNodes.contains(ElementParameterParser.getUNIQUENAME(node))) {
                    hasCXFSamlConsumer |= nodeUseSaml | nodeUseRegistry;
                } else {
                    hasCXFSamlProvider |= nodeUseSaml | nodeUseRegistry;
                    hasCXFRSSamlProviderAuthz |= nodeUseAuthz;
                }
            }
        }
        routeInfo.put("useSAM", useSAM); //$NON-NLS-1$
        routeInfo.put("useSL", useSL); //$NON-NLS-1$
        routeInfo.put("hasCXFUsernameToken", hasCXFUsernameToken); //$NON-NLS-1$
        routeInfo.put("hasCXFSamlConsumer", hasCXFSamlConsumer); //$NON-NLS-1$
        routeInfo.put("hasCXFSamlProvider", hasCXFSamlProvider); //$NON-NLS-1$
        routeInfo.put("hasCXFRSSamlProviderAuthz", hasCXFRSSamlProviderAuthz && !hasCXFComponent); //$NON-NLS-1$
        routeInfo.put("hasCXFComponent", hasCXFComponent); //$NON-NLS-1$

        // route OSGi DataSources
        routeInfo.put("dataSources", DataSourceConfig.getAliases(process)); //$NON-NLS-1$

        routeInfo.put("routelets", routelets); //$NON-NLS-1$

        return routeInfo;
    }

    @Override
    protected Set<String> getCompiledModuleNames() {
        return Collections.emptySet();
    }

    @Override
    protected void addOsgiDependencies(Analyzer analyzer, ExportFileResource libResource, ProcessItem processItem)
            throws IOException {
        final DependenciesResolver resolver = new DependenciesResolver(processItem);
        //exportPackage.append(getPackageName(processItem));
        // Add Route Resource Export packages
        // http://jira.talendforge.org/browse/TESB-6227

        // add manifest items
        analyzer.setProperty(Analyzer.REQUIRE_BUNDLE, resolver.getManifestRequireBundle(MANIFEST_ITEM_SEPARATOR));
        analyzer.setProperty(Analyzer.IMPORT_PACKAGE,
            resolver.getManifestImportPackage(MANIFEST_ITEM_SEPARATOR) + ",*;resolution:=optional"); //$NON-NLS-1$
        analyzer.setProperty(Analyzer.EXPORT_PACKAGE, resolver.getManifestExportPackage(MANIFEST_ITEM_SEPARATOR));

        if (GlobalServiceRegister.getDefault().isServiceRegistered(IRunProcessService.class)) {
            IRunProcessService processService = (IRunProcessService) GlobalServiceRegister.getDefault().getService(
                    IRunProcessService.class);
            ITalendProcessJavaProject talendProcessJavaProject = processService.getTalendProcessJavaProject();
            if (talendProcessJavaProject != null) {
                final IPath libPath = talendProcessJavaProject.getLibFolder().getLocation();
                // process external libs
                final List<URL> list = new ArrayList<URL>();
                for (String s : resolver.getManifestBundleClasspath(MANIFEST_ITEM_SEPARATOR)
                    .split(Character.toString(MANIFEST_ITEM_SEPARATOR))) {
                    if (!s.isEmpty()) {
                        list.add(libPath.append(s).toFile().toURI().toURL());
                    }
                }
                libResource.addResources(list);
            }
        }
    }
}
