package org.talend.camel.designer.generator;

import org.talend.core.ui.properties.tab.IDynamicProperty;
import org.talend.designer.core.ui.editor.properties.controllers.AbstractElementPropertySectionController;
import org.talend.designer.core.ui.editor.properties.controllers.generator.IControllerGenerator;

public class RouteInputProcessTypeGenerator implements IControllerGenerator {


	private IDynamicProperty dp;

	public AbstractElementPropertySectionController generate() {
		return new RouteInputProcessTypeController(dp);
	}

	public void setDynamicProperty(IDynamicProperty dp) {
		this.dp = dp;
	}

}
