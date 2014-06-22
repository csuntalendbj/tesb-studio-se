// ============================================================================
//
// Copyright (C) 2006-2012 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.routines.camelprovider;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.talend.commons.utils.io.FilesUtils;
import org.talend.core.model.routines.IRoutinesProvider;

/**
 * class global comment. Detailled comment
 */
public class CamelJavaRoutinesProvider implements IRoutinesProvider {

    /**
     * CamelJavaRoutinesProvider constructor comment.
     */
    public CamelJavaRoutinesProvider() {
    }

    public List<URL> getSystemRoutines() {
        List<URL> toReturn = FilesUtils.getFilesFromFolder(Activator.getDefault().getBundle(), "resources/java/routines",
                ".java", false, false);

        return toReturn;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.talend.core.model.routines.IRoutinesProvider#getTalendRoutinesFolder()
     */
    public URL getTalendRoutinesFolder() throws IOException {
        URL url = Activator.getDefault().getBundle().getEntry("resources/java/routines/system"); //$NON-NLS-1$
        return FileLocator.resolve(url);
    }

    public List<URL> getTalendRoutines() {
        return FilesUtils.getFilesFromFolder(Activator.getDefault().getBundle(), "resources/java/routines/system", "");
    }
}