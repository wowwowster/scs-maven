if (((typeof sword) === "undefined") || (sword == null)) window["sword"] = {};

sword.DEBUG = false;

sword.HOME_TAB_ID = "tabs-1";
sword.HOME_TAB_INDEX = 0;
sword.GSA_TAB_ID = "tabs-2";
sword.GSA_TAB_INDEX = 1;
sword.CONN_TAB_ID = "tabs-3";
sword.CONN_TAB_INDEX = 2;
// sword.AUTHN_TAB_ID = "tabs-4";
// sword.AUTHN_TAB_INDEX = 3;
sword.SB_TAB_ID = "tabs-4";
sword.SB_TAB_INDEX = 3;

sword.TABREF_COOKIE_NAME = "scstab";

sword.conf = null;
sword.tabIndex = 4;
sword.tabRefs = {};
sword.AddConnectorTab = null;
sword.AddCloudTab = null;

sword.init = function() {
	sword.tabs = $("#tabs").tabs({ activate: sword.recordTabActivation });
	sword.tabs.delegate("span.ui-icon-close", "click", sword.removeTabFromLi);
	$( "#tabs" ).tabs("disable", sword.GSA_TAB_INDEX);
	$( "#tabs" ).tabs("disable", sword.CONN_TAB_ID);
	$( "#tabs" ).tabs("disable", sword.AUTHN_TAB_INDEX);
	$( "#tabs" ).tabs("disable", sword.SB_TAB_INDEX);
	
	$("#AccountInfoSwitch>button").button({ icons: { secondary: "ui-icon-transfer-e-w" }, text: true });
	$("#HPSCSLogDload").button({ icons: { secondary: "ui-icon-circle-zoomin" }, text: false });
	$("#GSAMenuRegGSABtn").button({ icons: { secondary: "ui-icon-plusthick" }, text: false });
	$("#GSAMenuUploadFTPBtn").button({ icons: { secondary: "ui-icon-check" }, text: false });
	$("button.GSAMenuCommonsBtnConfirm").button({ icons: { secondary: "ui-icon-check" }, text: true });
	$("button.GSAMenuCommonsBtnCancel").button({ icons: { secondary: "ui-icon-cancel" }, text: true });
	$("button.GSAMenuViewCertDetails").button({ icons: { secondary: "ui-icon-search" }, text: true });
	$("button.GSAMenuCertDetailsClose").button({ icons: { secondary: "ui-icon-close" }, text: true });
	$("button#GSAMenuInfoRemoveGSA").button({ icons: { primary: "ui-icon-trash" }, text: true });
	$("button#SBRegisterBtn").button({ icons: { primary: "ui-icon-check" }, text: true });
	
	$( document ).tooltip({ items: ".tooltipped, [tooltipped]", content: sword.showDesc });
	
	//Bind events
	$(document).on("click", sword.toggleAccountInfo);
	$(document).on("click", "div#AccountInfoSwitch>button", sword.switchAccount);
	$(document).on("click", "#AddConn", sword.fillConnectorCreationTab);
	$(document).on("click", "#AddCloudSource", sword.showCloudCreationTab);
	$(document).on("click", "#GSAMenuRegGSABtn", sword.tryRegisterNewGSA);
	$(document).on("click", "#SBRegisterBtn", sword.registerNewSB);
	$(document).on("change","#GSAMenuUploadFTPNoGSA", sword.switchModes)
	$(document).on("click", "#GSAMenuUploadFTPBtn", sword.setFTPMode);
	$(document).on("click", ".GSAMenuCommonsBtnCancel", sword.buildGsaMenu);
	$(document).on("click", "button.GSAMenuViewCertDetails", sword.showCertDetails);
	$(document).on("click", "button.GSAMenuCertDetailsClose", sword.hideCertDetails);
	$(document).on("click", "button.GSAMenuCommonsBtnConfirm", sword.gsaRegMenuConfirm);
	$(document).on("click", "button#GSAMenuInfoRemoveGSA", sword.deleteGSA);
	$(document).on("click", "#GSAMenuACFeedergateAccessGrant>button", sword.grantFeedergateAccess);
	$(document).on("click", "#GSAMenuACDoRegAsCM>button", sword.registerCM);
	$(document).on("click", "#GSAMenuACURLPatternAdd>button", sword.addPatterns);
	$(document).on("click", "#CancelAdd", sword.removeTabFromDiv);
	$(document).on("click", ".cbedit", sword.createConnectorEditionTab);
	$(document).on("click", ".cbmon", sword.fillConnectorDashboardTab);
	$(document).on("click", ".cbdel", sword.deleteConnector);
	$(document).on("click", "#SaveAuthnConf", sword.submitAuthNConfig);
	$(document).on("click", "#CancelAuthn", sword.initAuthNForm);
	$(document).on("click", "#HPSCSLogDload", function(){ window.open("/SCS/secure/restconf/scs/log", "_blank"); });
	$(document).on("click", "#SCSDocBtn", "SCSDocLink", sword.ConnectorConfManager.prototype.openDocumentationLink);
	$(document).on("change", "#groupRetrieval, #groupRetrieval2", sword.copyGroupRetrValue);
	$(document).on("change", "input.HPEndpoints", sword.showEndpoints);
	$("#GSAMenuGSAInfo>form").on("submit", function(){ return false; });

	if (window.location.href.match(/[&\?]status=updated/)) {
		$("#" + sword.HOME_TAB_ID).find("div.cpline").remove();
		$("#" + sword.HOME_TAB_ID).find("div.HPSCSInfo").remove();
		$("#" + sword.HOME_TAB_ID).find("div.TabLoadSpinner").remove();
		$("#" + sword.HOME_TAB_ID).append("<div id=\"refresh\"><div>Refreshing configuration</div><div><img src=\"/static/img/progress.gif\" /></div></div>");
		sword.checkFreshness();
	} else {
		sword.loadConf();
	}
	
};

sword.toggleAccountInfo = function(event) {
	if ($(event.target).closest("#AccountInfoImg").length > 0) {
		event.stopPropagation();
		if ($("#AccountInfoDetails").is(":visible")) $("#AccountInfoDetails").hide();
		else $("#AccountInfoDetails").show();
	} else if ($(event.target).closest("#AccountInfoDetails").length === 0) {
		if ($("#AccountInfoDetails").is(":visible")) {
			event.stopPropagation();
			$("#AccountInfoDetails").hide();
		}
	}
};

sword.switchAccount = function(event) {
	$.ajax({
		url: "/SCS/secure/restconf/logout", 
		type: "GET", 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Logout failed", "Failed to log out");
		}, success: function( data, textStatus, jqXHR ) {
			window.location.reload();
		}
	});
};

sword.setLastActivatedTab = function(tid) {
	document.cookie = sword.TABREF_COOKIE_NAME + "=" + tid;
};

sword.recordTabActivation = function(event, ui) {
	var newTabId = ui.newPanel.attr("id");
	if ((newTabId===sword.HOME_TAB_ID) || (newTabId===sword.GSA_TAB_ID) || (newTabId===sword.CONN_TAB_ID) || (newTabId===sword.AUTHN_TAB_ID)) sword.setLastActivatedTab(newTabId);
	else {
		var tid = $("#" + newTabId).find("span.TabIdRef");
		if (tid.length > 0) sword.setLastActivatedTab(tid.text());
	}
}

sword.addTab = function(tabName) {
	sword.tabIndex++;
	var tabIndex = sword.tabIndex;
	if (sword.DEBUG) console.log("Creating tab #" + tabIndex);
	var tabContainerId = "tabs-" + tabIndex;
	$("#tabs").append("<div id=\"" + tabContainerId + "\" class=\"TabContainer\" ><div class=\"TabLoadSpinner\" ><div class=\"TabLoadSpinnerCont\" ><div class=\"TabLoadSpinnerMessage\" >Loading</div><div class=\"TabLoadSpinnerIcon\" ></div></div></div></div>");
	sword.tabRefs[tabContainerId] = {};
	var tabRef = sword.tabRefs[tabContainerId];
	tabRef.index = tabIndex;
	tabRef.containerId = tabContainerId;
	tabRef.container = $("#" + tabContainerId);
	$("#tablist").append("<li id=\"TabFor" + tabContainerId + "\" class=\"tabitem\" ><a href=\"#tabs-" + tabIndex + "\">" + tabName + "</a><span class=\"closabletabbtn ui-icon ui-icon-close\" role=\"presentation\">Remove Tab</span></li>");
	return tabRef;
};

sword.showTab = function(tabRef) {
	var tabIndex = 0;
	if (tabRef) {
		var allTabs = $(".tabitem");
		var tabCount = allTabs.length;
		var idToFind = "TabFor" + tabRef.containerId;
		for (var i = 0; i < tabCount; i++) {
			if (allTabs.get(i).id === idToFind) {
				tabIndex = i + 4;
				break;
			}
		}
	}
	$("#tabs").tabs("option", "active", tabIndex);
};

sword.removeTabFromLi = function(event) {
	sword.removeTab($(this).closest( "li" ).attr("id").substring(6));
};

sword.removeTabFromDiv = function(event) {
	sword.removeTab($(this).closest( "div.TabContainer" ).attr("id"));
};

sword.removeTab = function(tabContainerId) {
	var tabRef = sword.tabRefs[tabContainerId];
	$("#TabFor" + tabContainerId).remove();
	if (tabRef.tabManager) tabRef.tabManager.close();
	tabRef.container.remove();
	if (sword.AddConnectorTab && (sword.AddConnectorTab.containerId === tabRef.containerId)) sword.AddConnectorTab = null;
	if (sword.AddCloudTab && (sword.AddCloudTab.containerId === tabRef.containerId)) sword.AddCloudTab = null;
	delete sword.tabRefs[tabContainerId];
	sword.tabs.tabs("refresh");
	sword.restoreTab(null);
};

sword.restoreTab = function(tid) {
	if (tid === null) tid = "";
	if (tid.match(/^CONF_/)) {
		tid = tid.substring(5);
		if ($.inArray(sword.CONN_TAB_INDEX, $("#tabs").tabs("option", "disabled")) === -1) {
			var btnCont = $("#c_" + tid);
			if (btnCont.length > 0) btnCont.find("button.cbedit").click();
			else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
		} else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
	} else if (tid.match(/^MON_/)) {
		tid = tid.substring(4);
		if ($.inArray(sword.CONN_TAB_INDEX, $("#tabs").tabs("option", "disabled")) === -1) {
			var btnCont = $("#c_" + tid);
			if (btnCont.length > 0) btnCont.find("button.cbmon").click();
			else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
		} else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
	} else if (tid === sword.HOME_TAB_ID) {
		if ($.inArray(sword.HOME_TAB_INDEX, $("#tabs").tabs("option", "disabled")) === -1) $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
		else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
	} else if (tid === sword.GSA_TAB_ID) {
		if ($.inArray(sword.GSA_TAB_INDEX, $("#tabs").tabs("option", "disabled")) === -1) $("#tabs").tabs("option", "active", sword.GSA_TAB_INDEX);
		else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
	} else if (tid === sword.CONN_TAB_ID) {
		if ($.inArray(sword.CONN_TAB_INDEX, $("#tabs").tabs("option", "disabled")) === -1) $("#tabs").tabs("option", "active", sword.CONN_TAB_INDEX);
		else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
	} else if (tid === sword.AUTHN_TAB_ID) {
		if ($.inArray(sword.AUTHN_TAB_INDEX, $("#tabs").tabs("option", "disabled")) === -1) $("#tabs").tabs("option", "active", sword.AUTHN_TAB_INDEX);
		else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
	} else $("#tabs").tabs("option", "active", sword.HOME_TAB_INDEX);
};

sword.checkFreshness = function() {
	$.ajax({
		url: "/SCS/unsecure/restconf/scs/freshness", 
		type: "GET", 
		error: sword.ensureFreshness, 
		success: sword.ensureFreshness
	});
};

sword.ensureFreshness = function(data, textStatus, jqXHR) {
	if (((typeof data) !== "undefined") && (data !== null) && (data.IsFresh === true)) {
		window.location = window.location.href.replace(/\?.+/, "");
	} else {
		setTimeout(sword.checkFreshness, 500);
	}
};

sword.showDesc = function() {
	return $(this).parent().find(".tooltipval").html();
};

sword.showLoadingSpinner = function(container, message) {
	if (sword.DEBUG) console.log("Loading " + container.attr("id"));
	if ((typeof message) === "string") container.find("div.TabLoadSpinnerMessage").html(message);
	container.find("div.TabLoadSpinner").show();
};

sword.hideLoadingSpinner = function(container) {
	if (sword.DEBUG) console.log(container.attr("id") + " loaded");
	container.find("div.TabLoadSpinner").hide();
};

//Get config
sword.loadConf = function() {
	$.ajax({
		url: "/SCS/secure/restconf/scs/config", 
		type: "GET", 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Loading failed", "Failed to load configuration");
		}, 
		success: sword.onConfLoadOK, 
		complete: function(){
			sword.hideLoadingSpinner($("#" + sword.HOME_TAB_ID));
			$("#refresh").remove();
		}
	});
};

sword.stdErrorAlert = function(jqXHR, textStatus, errorThrown, title, message) {
	try {
		var errorObj = JSON.parse(jqXHR.responseText);
		$("body").append("<div id=\"dialog\" title=\"" + title + "\"><p><span class=\"ui-icon ui-icon-alert\" style=\"float:left; margin:0 7px 20px 0;\"></span>" + message + ": " + errorObj.ERROR + "</p></div>");
		if (errorObj.ERROR_STACK) {
			$("#dialog").find("p").append("<span><br/>Error stack: " + errorObj.ERROR_STACK + "</span>");
		}
	} catch (error) {
		$("body").append("<div id=\"dialog\" title=\"" + title + "\"><p><span class=\"ui-icon ui-icon-alert\" style=\"float:left; margin:0 7px 20px 0;\"></span>" + message + "<br/>Ajax text status: " + textStatus + "<br/>Ajax error: " + errorThrown.toString() + "</p></div>");
	}
	$("#dialog").dialog({
		resizable: false, 
		height:200, 
		modal: true, 
		close: function( event, ui ) { $( "#dialog" ).remove(); }, 
		buttons: {
			Ok: function() { $(this).dialog("close"); }
		}
	});
};

sword.onConfLoadOK = function(conf, textStatus, jqXHR) {

	sword.conf = conf;
	
	$( "#tabs" ).tabs("enable", sword.SB_TAB_INDEX);
	$("#AccountInfoDetails").hide();
	$("#ES_LoggedInAccount").children().appendTo("body");
	$("#AccountInfoName").text(sword.conf.CurrentUser);
	if (sword.conf.CurrentUser === "SCSAdmin") $("#AccountInfoRole").text("(local SCS admin)");
	else $("#AccountInfoRole").text("(GSA account)");
	
	var installedConnectorsCount = 0;
	for (var i in sword.conf.installedConnectors) installedConnectorsCount++;
	
	var configuredConnectorsCount = 0, samlAuthnConnectorsCount = 0, cookieCrackingConnectorsCount = 0, samlAuthzConnectorsCount = 0, cmAuthnConnectorsCount = 0, groupRetrConnectorsCount = 0, nameTransformersCount = 0, indexersCount = 0;
	for (var i in sword.conf.configuredConnectors) {
		configuredConnectorsCount++;
		var connectorClass = sword.conf.configuredConnectors[i];
		var connectorDef = sword.conf.installedConnectors[connectorClass];
		if (connectorDef.IsAuthNDelegator) samlAuthnConnectorsCount++;
		if (connectorDef.IsAuthNFormData) {
			samlAuthnConnectorsCount++;
			cmAuthnConnectorsCount++;
		}
		if (connectorDef.IsAuthNHeaders) cookieCrackingConnectorsCount++;
		if (connectorDef.IsAuthorizer) samlAuthzConnectorsCount++;
		if (connectorDef.IsGroupRetriever) groupRetrConnectorsCount++;
		if (connectorDef.IsNameTransformer) nameTransformersCount++;
		if (connectorDef.IsIndexer) indexersCount++;
	}
	
	$("#SCSDoc").append("<span><button class=\"Documentation\" id=\"SCSDocBtn\" >Documentation</button></span><span style=\"display: none\" ><a class=\"DocumentationLink\" id=\"SCSDocLink\" href=\"/documentation/index.html\" >doc</a></span>");
	$("#SCSDocBtn").button({ icons: { primary: "ui-icon-help" }, text: true });
	
	$("#HPNumConnInstalled").text(installedConnectorsCount);
	$("#HPNumConnConfigured").text(configuredConnectorsCount);
	$("#HPCookieCrackerConnectorsCount").text(cookieCrackingConnectorsCount);
	$("#HPSamlAuthZConnectorsCount").text(samlAuthzConnectorsCount);
	$("#HPSamlAuthNGrRetrCount").text(groupRetrConnectorsCount);
	$("#HPCMGrRetrCount").text(groupRetrConnectorsCount);
	$("#HPSamlAuthNConnectorsCount").text(samlAuthnConnectorsCount);
	$("#HPCMAuthNCount").text(cmAuthnConnectorsCount);
	$("#HPNameTransformersCount").text(nameTransformersCount);
	$("#HPIndexersCount").text(indexersCount);
	$("td.HPHttpEndpoints").hide();
	$("#HPHttpsEndpoints").prop("checked", true);
	$("#GSAMenuUploadFTPNoGSA").prop("checked", sword.conf.FtpMode);
	$("input[name=\"GSAMenuUploadFTPHost\"]").val(sword.conf.FtpHost);
	$("input[name=\"GSAMenuUploadFTPUsername\"]").val(sword.conf.FtpUsername);
	$("input[name=\"GSAMenuUploadFTPPassword\"]").val(sword.conf.FTPPassword);
	
	$("#GSAMenuACCConnectionInfo").html("Fetching GSA status <span class=\"LoadingIcon\"></span>");
	$("#GSAMenuACFeedergateAccessGrant>button").button({ icons: { primary: "ui-icon-unlocked" }, text: true, disabled: true });
	$("#GSAMenuACDoRegAsCM>button").button({ icons: { primary: "ui-icon-gear" }, text: true, disabled: true });
	$("#GSAMenuACURLPatternAdd>button").button({ icons: { primary: "ui-icon-plusthick" }, text: true, disabled: true });
	$.ajax({
		url: "/SCS/secure/restconf/scs/misc", 
		type: "GET", 
		success: function(scsMiscInfo, textStatus, jqXHR){
			$("#GSAMenuACCConnectionInfo").html("<span>&nbsp;</span>");
			$("#HPSamlAuthNHttpEndpoint").text(scsMiscInfo.scs.SamlAuthNHttpEndpoint);
			$("#HPSamlAuthNHttpsEndpoint").text(scsMiscInfo.scs.SamlAuthNHttpsEndpoint);
			$("#HPSamlAuthNResponderHttpEndpoint").text(scsMiscInfo.scs.SamlAuthNResponderHttpEndpoint);
			$("#HPSamlAuthNResponderHttpsEndpoint").text(scsMiscInfo.scs.SamlAuthNResponderHttpsEndpoint);
			$("#HPSamlAuthZHttpEndpoint").text(scsMiscInfo.scs.SamlAuthZHttpEndpoint);
			$("#HPSamlAuthZHttpsEndpoint").text(scsMiscInfo.scs.SamlAuthZHttpsEndpoint);
			$("#HPCookieHttpEndpoint").text(scsMiscInfo.scs.CookieHttpEndpoint);
			$("#HPCookieHttpsEndpoint").text(scsMiscInfo.scs.CookieHttpsEndpoint);
			$("#HPConnMgrHttpEndpoint").text(scsMiscInfo.scs.ConnMgrHttpEndpoint);
			$("#HPConnMgrHttpsEndpoint").text(scsMiscInfo.scs.ConnMgrHttpsEndpoint);
			$("#HPSCSDate").text(new Date(scsMiscInfo.scs.ServerDate).toLocaleString());
			$("#HPSCSUptime").text(scsMiscInfo.scs.Uptime);
			
			if (scsMiscInfo.gsa.connectivity.IsConfigured) {
				$("#GSAMenuACConnectivity, #GSAMenuACAuthn").removeClass("ui-icon-help");
				if (scsMiscInfo.gsa.connectivity.CanBeBound) {
					if (sword.conf.CurrentUser === "SCSAdmin") {
						$("#GSAMenuACCConnectionInfo").html("Currently connected as <span class=\"BI\">" + sword.conf.CurrentUser + "</span> - Click the login icon in the upper-right corner to authenticate with a GSA account.");
					} else {
						$("#GSAMenuACCConnectionInfo").html("Currently connected as <span class=\"BI\">" + sword.conf.CurrentUser + "</span>");
					}
					$("#GSAMenuACConnectivity").addClass("ui-icon-green").addClass("ui-icon-check");
					if (scsMiscInfo.gsa.connectivity.IsAuthenticated) {
						$("#GSAMenuACAuthn").addClass("ui-icon-green").addClass("ui-icon-check");
						if (scsMiscInfo.gsa.FeedergateAccessOK) {
							$("#GSAMenuACFeedergateAccess").text("OK");
						} else {
							$("#GSAMenuACFeedergateAccess").text("Unauthorized");
							$("#GSAMenuACFeedergateAccessGrant>button").button("option", "disabled", false);
						}
						if ((typeof scsMiscInfo.gsa.cm.name) === "string") {
							$("#GSAMenuACRegAsCM").text("Registered as " + scsMiscInfo.gsa.cm.name);
						} else {
							$("#GSAMenuACRegAsCM").text("Not registered");
							$("#GSAMenuACDoRegAsCM>button").button("option", "disabled", false);
						}
						if (scsMiscInfo.gsa.urlpatterns) {
							$("#GSAMenuACURLPattern").text("OK");
						} else {
							$("#GSAMenuACURLPattern").text("Not registered");
							$("#GSAMenuACURLPatternAdd>button").button("option", "disabled", false);
						}
					} else {
						$("#GSAMenuACAuthn").addClass("ui-icon-red").addClass("ui-icon-closethick");
					}
				} else {
					$("#GSAMenuACConnectivity").addClass("ui-icon-red").addClass("ui-icon-closethick");
					$("#GSAMenuACAuthn").addClass("ui-icon-help");
				}
			}
		}, error: function(){ $("#GSAMenuACCConnectionInfo").html("Failed to fetching GSA status"); }
	});
	
	sword.buildGsaMenu();
	sword.switchModes();
	sword.hideLoadingSpinner($("#" + sword.GSA_TAB_ID));
	$("#tabs").tabs("enable", sword.GSA_TAB_INDEX);
	
	var hasConfiguredConnectors = false;
	sword.conf.configuredGroupRetrievers = {};
	sword.conf.configuredCachableGroupRetrievers = {};
	sword.conf.configuredIndexers = {};
	
	if (sword.conf.authnConf && sword.conf.authnConf.groupRetrievers) {
		for (var i in sword.conf.authnConf.groupRetrievers) {
			var connectorId = sword.conf.authnConf.groupRetrievers[i];
			sword.conf.configuredGroupRetrievers[connectorId] = true;
			var connectorClass = sword.conf.configuredConnectors[connectorId];
			var connectorDef = sword.conf.installedConnectors[connectorClass];
			if (connectorDef.IsCachableGroupRetriever) sword.conf.configuredCachableGroupRetrievers[connectorId] = true;
		}
	}
	if (sword.conf.indexers) {
		for (var i in sword.conf.indexers) {
			var connectorId = sword.conf.indexers[i];
			sword.conf.configuredIndexers[connectorId] = true;
		}
	}
	
	if (sword.conf.configuredCloudSources) {
		for (var i in sword.conf.configuredCloudSources) {
			sword.addCloudSourceLink(sword.conf.configuredCloudSources[i]);
		}
	}
	
	for (var i in conf.configuredConnectors) {
		hasConfiguredConnectors = true;
		var connectorId = i;
		var connectorClass = conf.configuredConnectors[i];
		sword.addConnectorLink(connectorId, conf.installedConnectors[connectorClass], sword.conf.configuredCachableGroupRetrievers, sword.conf.configuredIndexers);
	}
	
	$(".cbedit").button({ icons: { secondary: "ui-icon-pencil" }, text: true });
	$(".cbmon").button({ icons: { secondary: "ui-icon-video" }, text: true });
	$(".cbdel").button({ icons: { secondary: "ui-icon-trash" }, text: true });
	
	$("#ES_ConTabTail").children().appendTo($("#" + sword.CONN_TAB_ID));
	
	$("#AddConn").button({ icons: { secondary: "ui-icon-plusthick" }, text: true });

	sword.hideLoadingSpinner($("#" + sword.CONN_TAB_ID));
	$("#tabs").tabs("enable", sword.CONN_TAB_INDEX);
	
	if (hasConfiguredConnectors) {
		sword.fillAuthNTab();
		sword.hideLoadingSpinner($("#" + sword.AUTHN_TAB_ID));
		$("#tabs").tabs("enable", sword.AUTHN_TAB_INDEX);
		//sword.fillAuthZTab();
	}
	
	var tabCook = document.cookie.match(new RegExp(sword.TABREF_COOKIE_NAME + "=([^;]+)"));
	if (tabCook) sword.restoreTab(tabCook[1]);
	else sword.restoreTab(null);
	
};

sword.addCloudSourceLink = function(sourceName) {
	//alert(sourceName);
	var divId = "c_" + sourceName;
	var html = $("#ES_ConTabConLink").children().clone();
	
	html.first().attr("id", divId);
	html.find(".cninfo").first().text(sourceName);
	html.find("button.cbmon").hide();
	
	$("#" + sword.SB_TAB_ID).find("div.header").first().after(html);
};

sword.showEndpoints = function(event) {
	if (sword.DEBUG) console.log($(this).attr("id"));
	if ($(this).attr("id") === "HPHttpEndpoints") {
		$("td.HPHttpsEndpoints").hide();
		$("td.HPHttpEndpoints").show();
	} else {
		$("td.HPHttpEndpoints").hide();
		$("td.HPHttpsEndpoints").show();
	}
};

sword.buildGsaMenu = function(knownGSA) {
	if ((typeof sword.conf.gsa_info) === "undefined" || $("#GSAMenuUploadFTPNoGSA").is(":checked") || sword.conf.gsa_info.DefaultHost === "") {
		$("#GSAMenuPickCert, #GSAMenuConfirmGSA, #GSAMenuGSAInfo").hide();
		$("#GSAMenuRegGSA").show();
	} else {
		$("#GSAMenuUploadFTP").hide();
		$("#GSAMenuRegGSA, #GSAMenuPickCert, #GSAMenuConfirmGSA").hide();
		$("#GSAMenuGSAInfo").find("input[name=\"GSAMenuInfoGsaDefHost\"]").val(sword.conf.gsa_info.DefaultHost);
		$("#GSAMenuGSAInfo").find("input[name=\"GSAMenuInfoGsaAdmHost\"]").val(sword.conf.gsa_info.AdminHost);
		$("#GSAMenuGSAInfo").find("input[name=\"GSAMenuInfoGsaSSL\"]").prop("checked", sword.conf.gsa_info.ssl);
		$("#GSAMenuGSAInfo").find("input[name=\"GSAMenuInfoID\"]").val(sword.conf.gsa_info.ID);
		$("#GSAMenuGSAInfo").find("input[name=\"GSAMenuInfoSoft\"]").val(sword.conf.gsa_info.Software);
		$("#GSAMenuGSAInfo").find("input[name=\"GSAMenuInfoSys\"]").val(sword.conf.gsa_info.System);
		$("#GSAMenuGSAInfo").show();
	}
};

sword.registerNewSB = function(event) {
	event.preventDefault();
		console.log("do we have", $("#SBMenuCustIdInputVal").text());

	$.ajax({
			url: "/SCS/secure/restconf/scs/sb", 
			type: "POST", 
			data: {
				"mode": "confirm", 
				"custid": $("#SBMenuCustIdInputVal").val(),
				"sourceid": $("#SBMenuSourceIdInputVal").val(),
				"sourcename" : $("#SBMenuSourceNameInputVal").val(),
				"svckey" : $("#SBMenuServiceKeyVal").val()
				}, 
				fileInputs: $('input[type="file"]'), 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "SB Registration failed", "Failed to register new SB Account");
			}, success: function(c, textStatus, jqXHR) {
				alert("ok");
				window.location = "/SCS/secure/manager.html?status=updated";
			}
		});
}

sword.gsaRegMenuConfirm = function() {
	if ($(this).text() === "Trust certificate") {
		var defaultGsaHost = $("#GSAMenuDefHostnameCont").text();
		var adminGsaHost = $("#GSAMenuAdmHostnameCont").text();
		var parent = $(this).closest(".GSAMenuCertSummary, .GSAMenuCertDetails");
		var i = parent.attr("id").substring(parent.hasClass("GSAMenuCertSummary") ? "GSAMenuCertSummary_".length : "GSAMenuCertDetails_".length);
		if (parent.hasClass("GSAMenuCertDetails")) $("button.GSAMenuCertDetailsClose").click();
		var sn = $("#GSAMenuPickCertRef_" + i).text();
		$.ajax({
			url: "/SCS/secure/restconf/scs/gsa", 
			type: "POST", 
			data: { "mode": "trustcert", "defaultgsahost": defaultGsaHost, "admingsahost": adminGsaHost, "sn": sn }, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "GSA Registration failed", "Failed to register new GSA");
			}, success: function(c, textStatus, jqXHR) {
				sword._tryRegisterNewGSA(defaultGsaHost, adminGsaHost, true);
			}
		});
	} else if ($(this).text() === "Register GSA") {
		$.ajax({
			url: "/SCS/secure/restconf/scs/gsa", 
			type: "POST", 
			data: {
				"mode": "confirm", 
				"defaultgsahost": $("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaDefHost").text(), 
				"admingsahost": $("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaAdmHost").text(), 
				"ssl": $("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaSSL").text()
			}, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "GSA Registration failed", "Failed to register new GSA");
			}, success: function(c, textStatus, jqXHR) {
				window.location = "/SCS/secure/manager.html?status=updated";
			}
		});
	}
};

sword.tryRegisterNewGSA = function() {
	sword._tryRegisterNewGSA($("#GSAMenuRegGSAInputDef").val(), $("#GSAMenuRegGSAInputAdm").val(), $("#GSAMenuRegGSAInputSSL").is(":checked"));
};

sword._tryRegisterNewGSA = function(defaultHost, adminHost, ssl) {
	$.ajax({
		url: "/SCS/secure/restconf/scs/gsa", 
		type: "POST", 
		data: { "mode": "probe", "defaultgsahost": defaultHost, "admingsahost": adminHost, "ssl": ssl ? "true" : "false" }, 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "GSA Registration failed", "Failed to register new GSA");
		}, success: sword.displayGSARegistrationAttemptResult
	});
};

sword.switchModes = function() {
	if($("#GSAMenuUploadFTPNoGSA").is(":checked")){
		$("#GSAMenuRegGSAInputDef").prop('disabled', true);
		$("#GSAMenuRegGSAInputAdm").prop('disabled', true);
		$("#GSAMenuRegGSAInputSSL").prop('disabled', true);
		$("#GSAMenuRegGSABtn").prop('disabled', true);
		
		$("#GSAMenuUploadFTPHost").prop('disabled', false);
		$("#GSAMenuUploadFTPUsername").prop('disabled', false);
		$("#GSAMenuUploadFTPPassword").prop('disabled', false);
		$("#GSAMenuUploadFTPBtn").prop('disabled', false);
	}else{
		$("#GSAMenuRegGSAInputDef").prop('disabled', false);
		$("#GSAMenuRegGSAInputAdm").prop('disabled', false);
		$("#GSAMenuRegGSAInputSSL").prop('disabled', false);
		$("#GSAMenuRegGSABtn").prop('disabled', false);
		
		$("#GSAMenuUploadFTPHost").prop('disabled', true);
		$("#GSAMenuUploadFTPUsername").prop('disabled', true);
		$("#GSAMenuUploadFTPPassword").prop('disabled', true);
		//$("#GSAMenuUploadFTPBtn").prop('disabled', true);
	}
};

sword.setFTPMode = function() {
	if($("#GSAMenuUploadFTPNoGSA").is(":checked"))sword._setFTPMode($("#GSAMenuUploadFTPHost").val(), $("#GSAMenuUploadFTPUsername").val(), $("#GSAMenuUploadFTPPassword").val());
};

sword._setFTPMode = function(Host, Username, Password) {
	$.ajax({
		url: "/SCS/secure/restconf/scs/ftp", 
		type: "POST", 
		data: { "mode": "confirm", "FTPHost": Host, "FTPUsername": Username, "FTPPassword": Password}, 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "FTP mode failed", "Failed to set FTP mode");
		}, success: function(c, textStatus, jqXHR) {
			window.location = "/SCS/secure/manager.html?status=updated";
		}
	});
};

sword.displayGSARegistrationAttemptResult = function(c, textStatus, jqXHR) {
	if (c.status === 0) {//success
		$("#GSAMenuUploadFTP").hide();
		$("#GSAMenuRegGSA, #GSAMenuPickCert, #GSAMenuGSAInfo").hide();
		$("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaDefHost").text(c.DefaultGsaHost);
		$("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaAdmHost").text(c.AdminGsaHost);
		$("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaSSL").text(c.ssl ? "true" : "false");
		$("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaLicID").text(c.gsa_info.ID);
		$("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaSys").text(c.gsa_info.System);
		$("#GSAMenuConfirmGSA").find("td.GSAMenuConfirmGsaSoft").text(c.gsa_info.Software);
		$("#GSAMenuConfirmGSA").show();
	} else if (c.status === 1) {//certificate not trusted
		$("#GSAMenuRegGSA, #GSAMenuConfirmGSA, #GSAMenuGSAInfo").hide();
		$("#GSAMenuPickCertChainContainer").html("<div style=\"display: none\" id=\"GSAMenuDefHostnameCont\" ></div><div style=\"display: none\" id=\"GSAMenuAdmHostnameCont\" ></div>");
		$("#GSAMenuDefHostnameCont").text(c.DefaultGsaHost);
		$("#GSAMenuAdmHostnameCont").text(c.AdminGsaHost);
		for (var i in c.certs) {
			var html = $("#ES_GsaRegCertSummary").children().clone();
			html.attr("id", "GSAMenuCertSummary_" + i);
			html.find("div.pn").text(c.certs[i].cn + ": ");
			html.find("button.GSAMenuViewCertDetails").attr("id", "GSAMenuViewCertDetails_" + i);
			$("#GSAMenuPickCertChainContainer").append(html);
			
			html = "<div style=\"display: none\" id=\"GSAMenuPickCertRef_" + i + "\" ></div>";
			$("#GSAMenuPickCertChainContainer").append(html);
			$("#GSAMenuPickCertRef_" + i).text(c.certs[i].raw_sn);
			
			html = $("#ES_GsaRegCertDetails").children().clone();
			html.attr("id", "GSAMenuCertDetails_" + i).hide();
			html.find("td.GSAMenuPickCertSubject").html(c.certs[i].issued_to);
			html.find("td.GSAMenuPickCertSN").html(c.certs[i].sn);
			html.find("td.GSAMenuPickCertIssuer").html(c.certs[i].issued_by);
			html.find("td.GSAMenuPickCertValFrom").text(c.certs[i].valnb);
			html.find("td.GSAMenuPickCertValTo").text(c.certs[i].valna);
			html.find("td.GSAMenuPickCertFPSHA256").html(c.certs[i].fingerprint_sha256);
			html.find("td.GSAMenuPickCertFPSHA1").html(c.certs[i].fingerprint_sha1);
			html.find("td.GSAMenuPickCertVersion").text(c.certs[i].version);
			html.find("td.GSAMenuPickCertPubKey").html(c.certs[i].pubkey);
			html.find("td.GSAMenuPickCertSigalg").text(c.certs[i].sigalg);
			html.find("td.GSAMenuPickCertSig").html(c.certs[i].signature);
			$("#GSAMenuPickCertChainContainer").append(html);
		}
		$("#GSAMenuPickCert").show();
	} else if (c.status === -1) {//unknown error
		sword.stdErrorAlert(null, c.error_message, c.error_type, "Registration failed", "Failed to register GSA");
	}
};

sword.showCertDetails = function() {
	var i = $(this).attr("id").substring("GSAMenuViewCertDetails_".length);
	$("#GSAMenuCertDetails_" + i).show();
};

sword.hideCertDetails = function() {
	$(this).parent().parent().parent().hide();
};

sword.grantFeedergateAccess = function() {
	sword.showLoadingSpinner($("#" + sword.GSA_TAB_ID), "Updating GSA feedergate access");
	$.ajax({
		url: "/SCS/secure/restconf/scs/gsa/feedergate", 
		type: "POST", 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.hideLoadingSpinner($("#" + sword.GSA_TAB_ID));
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Failed to grant access", "Failed to grant access to the SCS IP address");
		}, success: function(c, textStatus, jqXHR) {
			window.location.reload();
		}
	});
	return false;
};

sword.registerCM = function() {
	sword.showLoadingSpinner($("#" + sword.GSA_TAB_ID), "Registering a ConnectorManager");
	$.ajax({
		url: "/SCS/secure/restconf/scs/gsa/cm", 
		type: "POST", 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.hideLoadingSpinner($("#" + sword.GSA_TAB_ID));
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Failed to register CM", "The SCS could not be registered as a ConnectorManager in the GSA");
		}, success: function(c, textStatus, jqXHR) {
			window.location.reload();
		}
	});
	return false;
};

sword.addPatterns = function() {
	sword.showLoadingSpinner($("#" + sword.GSA_TAB_ID), "Updating GSA URL patterns");
	$.ajax({
		url: "/SCS/secure/restconf/scs/gsa/url-patterns", 
		type: "POST", 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.hideLoadingSpinner($("#" + sword.GSA_TAB_ID));
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Failed to add URL patterns", "Failed to add the SCS indexers URL patterns in the GSA");
		}, success: function(c, textStatus, jqXHR) {
			window.location.reload();
		}
	});
	return false;
};

sword.deleteGSA = function() {
	$.ajax({
		url: "/SCS/secure/restconf/scs/gsa", 
		type: "POST", 
		data: { "mode": "delete", "defaultgsahost": $("input[name=\"GSAMenuInfoGsaDefHost\"]").val(), "admingsahost": $("input[name=\"GSAMenuInfoGsaAdmHost\"]").val() }, 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "GSA Removal failed", "Failed to delete GSA from configuration");
		}, success: function(c, textStatus, jqXHR) {
			window.location = "/SCS/secure/manager.html?status=updated";
		}
	});
	return false;
};

sword.addConnectorLink = function(connectorId, connectorDef, configuredCachableGroupRetrievers, configuredIndexers) {
	var divId = "c_" + connectorId;
	var html = $("#ES_ConTabConLink").children().clone();
	
	html.first().attr("id", divId);
	html.find(".cninfo").first().text(connectorId);
	html.find(".cvinfo").first().text("(" + connectorDef.name + "-v" + connectorDef.version + ")");
	if (!(configuredCachableGroupRetrievers.hasOwnProperty(connectorId) || configuredIndexers.hasOwnProperty(connectorId))) html.find("button.cbmon").hide();
	
	$("#" + sword.CONN_TAB_ID).find("div.header").first().after(html);
};

sword.deleteConnector = function(event) {
	$(this).attr("disabled", "disabled");
	var cid = $(this).parent().parent().attr("id").substring(2);
	$("body").append("<div id=\"dialog-confirm\" title=\"Are you sure?\"><p><span class=\"ui-icon ui-icon-alert\" style=\"float:left; margin:0 7px 20px 0;\"></span>" + cid + " connector will be deleted from the SCS configuration.</p></div>");
	$("#dialog-confirm").dialog({
		resizable: false, 
		height:140, 
		modal: true, 
		close: function( event, ui ) { $( "#dialog-confirm" ).remove(); }, 
		buttons: {
			"Delete": function() {
				$(this).dialog("close");
				sword.doDeleteConnector(cid);
			}, Cancel: function() {
				$(this).dialog("close");
			}
		}
	});
};

sword.doDeleteConnector = function(cid) {
	$.ajax({
		url: "/SCS/secure/restconf/connector/delete", 
		type: "POST", 
		data: { "id": cid }, 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Deletion failed", "Failed to delete connector");
		}, 
		success: function(){ window.location.href = "/SCS/secure/manager.html?status=updated"; }
	});
};

sword.fillAuthNTab = function() {
	$("#SaveAuthnConf").button({ icons: { primary: "ui-icon-disk" }, text: true });
	$("#CancelAuthn").button({ icons: { primary: "ui-icon-cancel" }, text: true });
	sword.initAuthNForm();
	$("div.resizable").resizable();
};

sword.initAuthNForm = function() {
	var c = sword.conf.authnConf;
	if (c !== null) {
		$("#AuthType").val(c.type);
		if (c.kerberosAuthN) $("input[name=\"kerberosAuthN\"]").prop("checked", true);
		else $("input[name=\"kerberosAuthN\"]").prop("checked", false);
		if (c.groupRetrieval) $("input[name=\"groupRetrieval\"]").prop("checked", true);
		else $("input[name=\"groupRetrieval\"]").prop("checked", false);
		$("input[name=\"entityId\"]").val(c.entityId);
		$("input[name=\"trustDuration\"]").val(c.trustDuration);
		$("input[name=\"ConnMgrMCName\"]").val(c.cmMainConnectorName);
	}
};

sword.submitAuthNConfig = function() {
	$("#SaveAuthnConf").attr("disabled", "disabled");
	var authnConf = {};
	authnConf.type = $("#AuthType").val();
	authnConf.kerberosAuthN = $("input[name=\"kerberosAuthN\"]").prop("checked");
	authnConf.groupRetrieval = $("input[name=\"groupRetrieval\"]").prop("checked");
	authnConf.trustDuration = $("input[name=\"trustDuration\"]").val();
	authnConf.entityId = $("input[name=\"entityId\"]").val();
	authnConf.cmMainConnectorName = $("input[name=\"ConnMgrMCName\"]").val();
	
	$.ajax({
		url: "/SCS/secure/restconf/scs/authent", 
		type: "POST", 
		data: JSON.stringify(authnConf), 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Failed to save configuration", "Failed to save configuration");
		}, 
		success: function(){ window.location.href = "/SCS/secure/manager.html?status=updated"; }
	});
};

sword.copyGroupRetrValue = function(event) {
	var i = $(this).attr("id");
	if (i === "groupRetrieval") $("#groupRetrieval2").prop('checked', $(this).is(":checked"));
	else $("#groupRetrieval").prop('checked', $(this).is(":checked"));
};

//@deprecated
sword.fillAuthZTab = function() {

	var hasAuthorizer = false;
	for (var i in sword.conf.configuredConnectors) {
		var connectorClass = sword.conf.configuredConnectors[i];
		var installedConnector = sword.conf.installedConnectors[connectorClass];
		if (installedConnector.IsAuthorizer) {
			hasAuthorizer = true;
			break;
		}
	}

	if (hasAuthorizer) {
	
		var h = "<div class=\"cpline\" >";
		h += "<div class=\"pn tooltipped\" tooltipped=\"\" ><label for=\"globalAuthZTimeout\">Authorization process timeout: </label></div>";
		h += "<div class=\"pv resizable\" ><input name=\"globalAuthZTimeout\" type=\"text\" value=\"\" ></div>";
		h += "<div class=\"tooltipval\" >Allows to return a partial authorization status to the GSA when the authorization process is too slow.</div>";
		h += "</div>";
		$("#tabs-3").append(h);
		
		h = "<div class=\"cpline\" >";
		h += "<div class=\"pn tooltipped\" tooltipped=\"\" ><label for=\"unknownURLsDefaultAccess\">Authorization status for unknown URLs: </label></div>";
		h += "<div class=\"pv resizable\" ><select name=\"unknownURLsDefaultAccess\" ><option value=\"Deny\" >Deny</option><option value=\"Indeterminate\" >Indeterminate</option></select></div>";
		h += "<div class=\"tooltipval\" >When a URL does not match any of the URL patterns that have been defined for the authorization connectors, returning <i>Deny</i> would prevent the GSA from attemptings any other authorization mechanism - returning <i>Indeterminate</i> would let the GSA attempt other authorization mechanism (including authorization HEAD requests)<br>It is recommended to use <i>Deny</i> if not other SAML provider are configured for authorization.</div>";
		h += "</div>";
		$("#tabs-3").append(h);
		
		h = "<div class=\"cpline\" >";
		h += "<div class=\"pn\" ><label for=\"authorizers\">Add a new connector for authorization: </label></div>";
		h += "<div class=\"pv resizable\" ><select name=\"authorizers\" ><option value=\"nothing\" > </option>";
		for (var i in sword.conf.configuredConnectors) {
			var connectorClass = sword.conf.configuredConnectors[i];
			var installedConnector = sword.conf.installedConnectors[connectorClass];
			if (installedConnector.IsAuthorizer) {
				h += "<option value=\"" + i + "\" >" + i + "</option>";
			}
		}
		h += "</select></div>";
		h += "</div>";
		$("#tabs-3").append(h);
		
		$("div.resizable").resizable();

	} else {
		$("#tabs-3").append("<div class=\"noconnector\" >Connectors currently configured do not support authorization.</div>");
	}
	
};

sword.showCloudCreationTab = function(event) {
	var tabRef = null;
	if (sword.AddCloudTab) {
		tabRef = sword.AddCloudTab;
		tabRef.container.empty();
	} else {
		tabRef = sword.addTab("Add Cloud Source");
		sword.AddCloudTab = tabRef;
	}
		sword.hideLoadingSpinner(tabRef.container);
tabRef.container.append($("#ES_CloudAddTab").children().clone());
$("#tabs").tabs("refresh");
	sword.showTab(tabRef);
};

sword.fillConnectorCreationTab = function(event) {

	var tabRef = null;
	if (sword.AddConnectorTab) {
		tabRef = sword.AddConnectorTab;
		tabRef.container.empty();
	} else {
		tabRef = sword.addTab("Add Connector");
		sword.AddConnectorTab = tabRef;
	}

	sword.hideLoadingSpinner(tabRef.container);
	tabRef.container.append($("#ES_ConCreateTab").children().clone());
	tabRef.container.find("select.ACType").attr("id", "ACType");
	tabRef.container.find("button.CancelAdd").attr("id", "CancelAdd");
	for (var i in sword.conf.installedConnectors) {
		$("#ACType").append("<option value=\"" + i + "\" >" + sword.conf.installedConnectors[i].name + " (v" + sword.conf.installedConnectors[i].version + ")</option>");
	}
	$(document).on("change", "#ACType", tabRef, sword.updateTabToConnectorEditionTab);
	$("#CancelAdd").button({ icons: { primary: "ui-icon-cancel" }, text: true });
	
	//Refresh tabs
	$("#tabs").tabs("refresh");
	sword.showTab(tabRef);
};

/* Called form Connector creation tab to transform it into a connector edition type once the user has selected a connector type */
sword.updateTabToConnectorEditionTab = function(event) {
	var tabRef = event.data;
	$(document).off("change", "#ACType");
	sword.AddConnectorTab = null;
	$.ajax({
		url: "/SCS/secure/restconf/connector/config", 
		type: "GET", 
		data: { "class": $("#ACType").val() }, 
		context: tabRef, 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Loading failed", "Failed to load connector configuration");
		}, 
		success: sword.onConnectorConfLoadOK
	});
};

sword.createConnectorEditionTab = function(event) {

	var tabRef = sword.addTab("Configure Connector");
	
	//Refresh tabs
	$("#tabs").tabs("refresh");
	sword.showTab(tabRef);
	
	var connId = $(this).parent().parent().attr("id").substring(2);

	$.ajax({
		url: "/SCS/secure/restconf/connector/config", 
		type: "GET", 
		data: { "id": connId }, 
		context: tabRef, 
		error: function( jqXHR, textStatus, errorThrown ) {
			sword.hideLoadingSpinner(tabRef.container);
			sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Loading failed", "Failed to load connector configuration");
		}, 
		success: sword.onConnectorConfLoadOK
	});
};

sword.onConnectorConfLoadOK = function(c, textStatus, jqXHR) {
	var tabRef = this;
	var ccm = new sword.ConnectorConfManager(tabRef, c);
	tabRef.tabManager = ccm;
	ccm.init();

};

sword.ConnectorConfManager = function(tr, cc) {
	this.tabIndex = tr.index;
	this.containerId = tr.containerId;
	this.container = tr.container;
	this.connectorConf = cc;
	this.connectorConfFormId = "ConnectorConfForm" + this.tabIndex;
	this.documentationBtnId = "Documentation" + this.tabIndex;
	this.documentationLinkId = "DocumentationLink" + this.tabIndex;
	this.connectorDefFieldsetId = "ConnectorDef" + this.tabIndex;
	this.connectorParamsFieldsetId = "ConnectorParams" + this.tabIndex;
	this.grCacheConfigFieldsetId = "GRCacheConfig" + this.tabIndex;
	this.indexerConfigFieldsetId = "IndexerConfig" + this.tabIndex;
	this.saveConfBtnId = "SaveConnectorConf" + this.tabIndex;
	this.cancelConfBtnId = "CancelConnector" + this.tabIndex;
	this.useForGrCheckboxId = "UseForGR" + this.tabIndex;
	this.useForPushCheckboxId = "UseForPush" + this.tabIndex;
	this.connectorConfParamIdPrefix = "cp_" + this.tabIndex + "_";
	this.pushConfParamIdPrefix = "PushConf_" + this.tabIndex + "_";
	this.isSubmitting = false;
};
sword.ConnectorConfManager.prototype = {

	init: function() {
	
		this.container.on("click", ".mvmore", this.addRow);
		this.container.on("click", ".mvless", this.remRow);
		this.container.on("change", ".ExclPerDays", this, this.updateIndexerScheduleEntry);
		$(document).on("click", "#" + this.documentationBtnId, this.documentationLinkId, this.openDocumentationLink);
		$(document).on("click", "#" + this.saveConfBtnId, this, this.submitConnectorConfig);
		$(document).on("click", "#" + this.cancelConfBtnId, sword.removeTabFromDiv);
		$(document).on("click", "#" + this.useForGrCheckboxId, this.grCacheConfigFieldsetId, this.toggleGroupRetrievalConfVisibility);
		$(document).on("click", "#" + this.useForPushCheckboxId, this.indexerConfigFieldsetId, this.togglePushConfVisibility);
		$(document).on("submit", "#" + this.connectorConfFormId, this, this.checkJSSubmit);
	
		var connectorDef = sword.conf.installedConnectors[this.connectorConf.className];
		$("#TabFor" + this.containerId + " > a").html("Configuring connector");
		this.container.html($("#ES_ConEditTab").children().clone());
		this.container.find("form").attr("id", this.connectorConfFormId);
		var form = $("#" + this.connectorConfFormId);
		
		//Hidden inputs
		form.find("input[name=\"class\"]").val(this.connectorConf.className);
		form.find("fieldset.ConnectorDef").attr("id", this.connectorDefFieldsetId);
		var connDef = $("#" + this.connectorDefFieldsetId);

		this.showDocumentationLink(connectorDef.name);
		
		//Connector ID (editable if new connector)
		var isNew = ((typeof this.connectorConf.id) === "undefined") || (this.connectorConf.id === null);
		if (isNew) {
			$("#TabFor" + this.containerId + " > a").html("Configuring " + connectorDef.name);
			this.container.find(".HeaderText").html("Configuring " + connectorDef.name + " connector");
			this.container.find(".IdAnchor").remove();
			this.container.find("span.ui-icon-locked.unmodifiableid").remove();
		} else {
			$("#TabFor" + this.containerId + " > a").html("Configuring <span style=\"font-style: italic\">#" + this.connectorConf.id + "</span>");
			this.container.find(".HeaderText").html("Configuring connector <span style=\"font-style: italic\">#" + this.connectorConf.id + "</span>");
			this.container.find("input[name=\"cid\"]").val(this.connectorConf.id).attr("readonly", "");
			this.container.append("<span class=\"TabIdRef\" ></span>");
			this.container.find("span.TabIdRef").text("CONF_" + this.connectorConf.id);
			sword.setLastActivatedTab("CONF_" + this.connectorConf.id);
		}
	
		//Connector modes
		this.container.find("input[name=\"UseForGR\"]").attr("id", this.useForGrCheckboxId);
		this.container.find("input[name=\"UseForPush\"]").attr("id", this.useForPushCheckboxId);
	
		var isConfiguredAuthenticator = false;
		var isConfiguredGroupRetriever = false;
		var isConfiguredIndexer = false;
		if (!isNew) {
			isConfiguredAuthenticator = (sword.conf.authnConf.authenticator && (sword.conf.authnConf.authenticator === this.connectorConf.id));
			isConfiguredGroupRetriever = (sword.conf.configuredGroupRetrievers[this.connectorConf.id] === true);
			isConfiguredIndexer = (sword.conf.configuredIndexers[this.connectorConf.id] === true);
		}
		
		var useForAuthN = this.container.find(".UseForAuthN").first();
		if (isConfiguredAuthenticator) useForAuthN.prop("checked", true);
		var useForGR = $("#" + this.useForGrCheckboxId);
		if (isConfiguredGroupRetriever) useForGR.prop("checked", true);
		var useForPush = $("#" + this.useForPushCheckboxId);
		if (isConfiguredIndexer) useForPush.prop("checked", true);
		
		if (!(connectorDef.IsAuthNDelegator || connectorDef.IsAuthNFormData || connectorDef.IsAuthNHeaders)) {
			useForAuthN.attr("disabled", "disabled");
			this.container.find("label[for=\"UseForAuthN\"]").css("color", "#aaa");
		}
		if (!connectorDef.IsGroupRetriever) {
			useForGR.attr("disabled", "disabled");
			this.container.find("label[for=\"UseForGR\"]").css("color", "#aaa");
		}
		if (!connectorDef.IsIndexer) {
			useForPush.attr("disabled", "disabled");
			this.container.find("label[for=\"UseForPush\"]").css("color", "#aaa");
		}
	
		//Namespace
		if (!(isNew || this.connectorConf.namespace === null)) this.container.find("input[name=\"ns\"]").val(this.connectorConf.namespace);
	
		var availableNameTransformers = new Array();
		for (var i in sword.conf.configuredConnectors) {
			var connectorClass = sword.conf.configuredConnectors[i];
			var installedConnector = sword.conf.installedConnectors[connectorClass];
			if (installedConnector.IsNameTransformer && (isNew || (this.connectorConf.id !== i))) availableNameTransformers.push(i);
		}
		
		//Name Transformers
		if (availableNameTransformers.length > 0) {
			connDef.append($("#ES_ConEditTabNameTransformers").children().clone());
			for (var i in availableNameTransformers) connDef.find("select[name=\"nameTransformer\"]").append("<option value=\"" + availableNameTransformers[i] + "\" >" + availableNameTransformers[i] + "</option>");
			if (!(isNew || this.connectorConf.nameTransformer === null)) this.container.find("select[name=\"nameTransformer\"]").val(this.connectorConf.nameTransformer);
		}
		
		form.find("fieldset.ConnectorParams").attr("id", this.connectorParamsFieldsetId);
		var connParams = $("#" + this.connectorParamsFieldsetId);
		connParams.find("legend").text(connectorDef.name + " parameters");
		//Config params
		var cp = null;
		for (var i in this.connectorConf.confParams) {
			
			var cpId = this.connectorConfParamIdPrefix + i;
		
			cp = this.connectorConf.confParams[i];
		
			connParams.append("<div id=\"" + cpId + "\" class=\"cpline\" ></div>");
			$("#" + cpId).append("<div class=\"pn tooltipped\" tooltipped=\"\" ><label for=\""+cp.name+"\" >"+cp.label+"</label>: </div><div class=\"pv\" ></div><div class=\"tooltipval\" >" + cp.description + "</div>");
			
			var valCont = $("#" + cpId + " > div.pv");
			valCont.addClass("CT_" + cp.type);
			if (cp.isMandatory) {
				valCont.after("<span class=\"ui-icon ui-icon-flag\" ></span>");
				valCont.addClass("pmandatory");
			}
			
			if (cp.type === "ENUM") {
				var h = "<select name=\""+cp.name+"\" >";
				for (var j in cp.permittedValues) h += "<option value=\"" + cp.permittedValues[j] + "\" >" + cp.permittedValues[j] + "</option>";
				h += "</select>";
				valCont.append(h);
				valCont.addClass("resizable");
				if (!(isNew || (cp.value === null))) valCont.find("select").val(cp.value);
			} else if (cp.type === "BOOLEAN") {
				valCont.append("<input name=\""+cp.name+"\" type=\"checkbox\" value=\"on\" >");
				if (!(isNew || (cp.value !== "true"))) valCont.find("input[name=\""+cp.name+"\"]").prop("checked", true);
			} else if (cp.type === "FILE") {
				valCont.append("<input name=\""+cp.name+"\" type=\"file\" value=\"\" placeholder=\"bla\" >");
			} else if (cp.type === "LARGE_STRING") {
				valCont.append("<textarea name=\""+cp.name+"\" value=\"\" >");
				if (!(isNew || (cp.value === null))) valCont.find("textarea[name=\""+cp.name+"\"]").val(cp.value);
				valCont.addClass("resizable");
			} else {
				valCont.append("<input name=\""+cp.name+"\" type=\"" + (cp.isEncrypted ? "password" : "text") + "\" value=\"\" >");
				if (!(isNew || (cp.value === null))) valCont.find("input[name=\""+cp.name+"\"]").val(cp.value);
				valCont.addClass("resizable");
			}
			
			if (cp.isMultivalue) {
				$("#" + cpId).after("<div id=\"" + cpId + "_mv\" class=\"mv\" ><div class=\"mvless\" ></div><div class=\"mvmore\" ></div></div>");
				
				if (!(((typeof cp.values) === "undefined") || (cp.values === null))) {
					var nv = cp.values.length;
					if (nv > 1) {
						var moreBtn = $("#" + cpId + "_mv").find(".mvmore").first();
						for (var j=1; j<nv; j++) this.addRow.call(moreBtn);
						window["tmpVarToDel"] = cp.values;
						$("#" + cpId).find("input,select").val(tmpVarToDel[0]);
						this.container.find("." + cpId + "_copy").each(function(index){
							$(this).find("input,select").val(tmpVarToDel[index+1]);
						});
					} else if (nv == 1) {
						this.container.find("#" + cpId).find("input,select").val(cp.values[0]);
					}
				}
			}
			
		}
		
		//Cache refresh interval
		if (this.connectorConf.CachableGroupRetriever) {
			form.append($("#ES_ConEditTabGR").children().clone());
			form.find("fieldset.GRCacheConfig").attr("id", this.grCacheConfigFieldsetId);
			$("#" + this.grCacheConfigFieldsetId).find("label").attr("for", this.connectorConf.CachableGroupRetriever.name).html(this.connectorConf.CachableGroupRetriever.label);
			$("#" + this.grCacheConfigFieldsetId).find("input[name=\"CachableGroupRetrieverName\"]").attr("name", this.connectorConf.CachableGroupRetriever.name).val(this.connectorConf.CachableGroupRetriever.value);
			$("#" + this.grCacheConfigFieldsetId).find("div.tooltipval").html(this.connectorConf.CachableGroupRetriever.description);
			if (!isConfiguredGroupRetriever) $("#" + this.grCacheConfigFieldsetId).hide();
		}
		
		//Indexer
		if (connectorDef.IsIndexer) {
			form.append("<fieldset id=\"" + this.indexerConfigFieldsetId + "\" class=\"IndexerConfig MgrConfSection\" ><legend>Indexing</legend></fieldset>");
			var cp = null;
			for (var i in this.connectorConf.IndexerConf) {
			
				cp = this.connectorConf.IndexerConf[i];
				var cpId = this.pushConfParamIdPrefix + i;
			
				$("#" + this.indexerConfigFieldsetId).append("<div id=\"" + cpId + "\" class=\"cpline\" ></div>");
				$("#" + cpId).append("<div class=\"pn tooltipped\" tooltipped=\"\" ><label for=\""+cp.name+"\" >"+cp.label+"</label>: </div><div class=\"pv\" ></div><div class=\"tooltipval\" >" + cp.description + "</div>");
				
				var valCont = $("#" + cpId + " > div.pv");
				valCont.addClass("CT_" + cp.type);
				if (cp.isMandatory) {
					$("#" + cpId + " > div.pn").addClass("pmandatory");
					valCont.addClass("pmandatory");
				}
				
				if (cp.type === "ENUM") {
					if(cp.name == "CloudSource") {
						var hi = "<select name=\""+cp.name+"\" >";
							$.ajax({
							url: "/SCS/secure/restconf/scs/sb/getCloudSources", 
							type: "GET", 
							async: false,
							success: function(scs, textStatus, jqXHR){
								var sources = scs.cloudsources;
								for(var i=0;i<sources.length;i++)
									hi += "<option value=\"" + sources[i].sourceid + "\">" + sources[i].sourcename + "</option>";
								
							}});
							hi += "</select>";
							valCont.append(hi);

					} else {
						var h = "<select name=\""+cp.name+"\">";
						for (var j in cp.permittedValues) h += "<option value=\"" + cp.permittedValues[j] + "\">" + cp.permittedValues[j] + "</option>";
						h += "</select>";
						valCont.append(h);
						valCont.addClass("resizable");
						if (!(isNew || (cp.value === null))) valCont.find("select").val(cp.value);
					}
					
				} else if (cp.type === "BOOLEAN") {
					valCont.append("<input name=\""+cp.name+"\" type=\"checkbox\" value=\"on\" >");
					if (!(isNew || (cp.value !== "true"))) valCont.find("input[name=\""+cp.name+"\"]").prop("checked", true);
				} else if (cp.type === "FILE") {
					valCont.append("<input name=\""+cp.name+"\" type=\"file\" value=\"\" placeholder=\"bla\" >");
				} else if (cp.type === "LARGE_STRING") {
					valCont.append("<textarea name=\""+cp.name+"\" value=\"\" >");
					if (!(isNew || (cp.value === null))) valCont.find("textarea[name=\""+cp.name+"\"]").val(cp.value);
					valCont.addClass("resizable");
				} else {
					valCont.append("<input name=\""+cp.name+"\" type=\"" + (cp.isEncrypted ? "password" : "text") + "\" form=\"" + this.connectorConfFormId + "\" >");
					if (cp.value && (cp.value !== null)) valCont.find("input[name=\""+cp.name+"\"]").val(cp.value);
					valCont.addClass("resizable");
				}
				
				if (cp.isMultivalue) {
					$("#" + cpId).after("<div id=\"" + cpId + "_mv\" class=\"mv\" ><div class=\"mvless\" ></div><div class=\"mvmore\" ></div></div>");
					
					if (!(((typeof cp.values) === "undefined") || (cp.values === null))) {
						var nv = cp.values.length;
						if (nv > 1) {
							var moreBtn = $("#" + cpId + "_mv").find(".mvmore").first();
							for (var j=1; j<nv; j++) this.addRow.call(moreBtn);
							window["tmpVarToDel"] = cp.values;
							$("#" + cpId).find("input,select").val(tmpVarToDel[0]);
							this.container.find("." + cpId + "_copy").each(function(index){
								$(this).find("input,select").val(tmpVarToDel[index+1]);
							});
						} else if (nv == 1) {
							$("#" + cpId).find("input,select").val(cp.values[0]);
						}
					}
				}
			}

			//Indexing interval
			$("#" + this.indexerConfigFieldsetId).append($("#ES_ConEditTabIndexInterval").children().clone());
			this.container.find("input[name=\"throughput\"]").spinner( { "min": -1 } );

			//Push schedule
			var sched0 = $("#ES_ConEditTabIndexSchedule0").children().clone();
			sched0.find("div.ExclPer").attr("id", "ExclPer" + this.tabIndex + "_1");
			$("#" + this.indexerConfigFieldsetId).append(sched0);
			
			if (this.connectorConf.schedule && this.connectorConf.schedule.periods && (this.connectorConf.schedule.periods.length > 0)) {
				for (var i=0; i<this.connectorConf.schedule.periods.length; i++) {
					var entryIndex = i + 1;
					if (i > 0) {//First one already exists
						var schedX = $("#ES_ConEditTabIndexScheduleX").children().clone();
						schedX.find("div.ExclPer").attr("id", "ExclPer" + this.tabIndex + "_" + entryIndex);
						$("#" + this.indexerConfigFieldsetId).append(schedX);
					}
					this.addNewIndexerScheduleEntry($("#ExclPer" + this.tabIndex + "_" + entryIndex));
					this.container.find("select[name=\"ExclPerDays" + this.tabIndex + "_" + entryIndex + "\"]").val("" + this.connectorConf.schedule.periods[i].day);
					var e = this.container.find("select[name=\"ExclPerFrom" + this.tabIndex + "_" + entryIndex + "\"]");
					e.val("" + this.connectorConf.schedule.periods[i].start);
					e.prop("disabled", false);
					e = this.container.find("input[name=\"ExclPerFor" + this.tabIndex + "_" + entryIndex + "\"]");
					e.spinner( "value", this.connectorConf.schedule.periods[i].duration );
					e.spinner( "option", "disabled", false );
				}
				entryIndex = this.connectorConf.schedule.periods.length + 1;
				var schedLast = $("#ES_ConEditTabIndexScheduleX").children().clone();
				schedLast.find("div.ExclPer").attr("id", "ExclPer" + this.tabIndex + "_" + entryIndex);
				$("#" + this.indexerConfigFieldsetId).append(schedLast);
				this.addNewIndexerScheduleEntry($("#ExclPer" + this.tabIndex + "_" + entryIndex));
			} else {
				this.addNewIndexerScheduleEntry($("#ExclPer" + this.tabIndex + "_1"));
			}
			
			var intervalInput = this.container.find("input[name=\"interval\"]");
			intervalInput.spinner( { "min": 0 } );
			if (this.connectorConf.interval) intervalInput.spinner( "value", this.connectorConf.interval );
			
			var throughputInput = this.container.find("input[name=\"throughput\"]");
			throughputInput.spinner( { "min": -1 } );
			if (this.connectorConf.throughput) throughputInput.spinner( "value", this.connectorConf.throughput );

			if (!isConfiguredIndexer) $("#" + this.indexerConfigFieldsetId).hide();
		}
		
		this.container.append("<div class=\"cpline\" ><div class=\"pn\" >&nbsp;</div><div class=\"pv\" ><button id=\"" + this.saveConfBtnId + "\" class=\"SaveConnectorConf\" >Save configuration</button></div></div>");
		this.container.append("<div class=\"cpline\" style=\"padding-bottom: 24px\" ><div class=\"pn\" >&nbsp;</div><div class=\"pv\" ><button id=\"" + this.cancelConfBtnId + "\" class=\"CancelConnector\" >Cancel</button></div></div>");
		$("#" + this.saveConfBtnId).button({
			icons: { primary: "ui-icon-disk" },
			text: true
		});
		$("#" + this.cancelConfBtnId).button({
			icons: { primary: "ui-icon-cancel" },
			text: true
		});
		this.container.find("div.resizable").resizable();
		
		sword.hideLoadingSpinner(this.container);
		
	}, close: function() {
		this.container.off("click", ".mvmore");
		this.container.off("click", ".mvless");
		this.container.off("change", ".ExclPerDays");
		$(document).off("click", "#" + this.documentationBtnId);
		$(document).off("click", "#" + this.saveConfBtnId);
		$(document).off("click", "#" + this.cancelConfBtnId);
		$(document).off("click", "#" + this.useForGrCheckboxId);
		$(document).off("click", "#" + this.useForPushCheckboxId);
		$(document).off("submit", "#" + this.connectorConfFormId);
	}, checkJSSubmit: function(event) {
		return event.data.isSubmitting;
	}, showDocumentationLink: function(connectorName) {
		var docUrl = "/documentation/connectors/" + encodeURIComponent(connectorName) + "/index.html";
		$.ajax({
			url: docUrl, 
			type: "GET", 
			context: this, 
			success: function() {
				var connDocElement = this.container.find(".ConnDoc").first();
				connDocElement.append("<span><button class=\"Documentation\" id=\"" + this.documentationBtnId + "\" >Documentation</button></span><span style=\"display: none\" ><a class=\"DocumentationLink\" id=\"" + this.documentationLinkId + "\" href=\"" + docUrl + "\" >doc</a></span>");
				connDocElement.find(".Documentation").first().button({
					icons: { primary: "ui-icon-help" }, 
					text: true
				});
			}
		});
	}, remRow: function(event) {
		var id = $(this).parent().attr("id").replace(/_mv$/, "");
		$("." + id + "_copy").last().remove();
	}, addRow: function(event) {
		var id = $(this).parent().attr("id").replace(/_mv$/, "");
		var rowToCopy = $("#"+id).clone().removeAttr("id").addClass(id + "_copy");
		rowToCopy.find("div.pn").empty();
		rowToCopy.find("input,select").val("");
		$(this).parent().before(rowToCopy);
	}, openDocumentationLink: function(event) {
		window.open($("#" + event.data).attr("href"), "_blank");
	}, submitConnectorConfig: function(event) {
	
		var container = event.data.container;

		var isNew = container.find(".IdAnchor").length === 0;

		//Remove all error messages
		container.find(".errormess").remove();
		
		//Look for invalid config
		//	Invalid ID
		if (isNew) {
			var idInput = container.find("input[type=\"text\"][name=\"cid\"]");
			if (idInput.length > 0) {
				var cid = idInput.val();
				if (!cid.match(/^[a-z_][a-z_0-9\\-]{0,63}$/)) {
					idInput.parent().after("<div class=\"errormess\" >Connector IDs must match ^[a-z_][a-z_\\d\\-]{0,63}$</div>");
				} else if (!((typeof sword.conf.configuredConnectors[cid]) === "undefined")) {
					idInput.parent().after("<div class=\"errormess\" >A connector with this ID already exists</div>");
				}
			}
		}
		//	Empty mandatory params
		container.find("div.pv.pmandatory").each(function(index) {
			if ($(this).is(":visible")) {//Hidden parameters are not part of the config
				var input = $(this).find("select, input, textarea").first();
				if (!((input.attr("type") === "file") || (input.val().length > 0))) 
					$(this).after("<div class=\"errormess\" >Mandatory parameters cannot be left empty</div>");
			}
		});
		
		if (container.find(".errormess").length === 0) {
			
			if (container.find("select[name=\"nameTransformer\"]").val() === "notaconnector") container.find("select[name=\"nameTransformer\"]").remove();
			
			//Reformat multivalue inputs
			if (sword.DEBUG) console.log("Reformatting multivalue parameters");
			container.find(".mv").each(function(index) {
				var cpid = $(this).attr("id").replace(/_mv$/, "");
				if (sword.DEBUG) console.log("Processing item #" + cpid);
				var originalInput = $("#" + cpid).find("select, input").first();
				var cpName = originalInput.attr("name");
				if (sword.DEBUG) console.log("Input name: " + cpName);
				window["tmpVarToDel"] = [ originalInput.val() ];
				container.find("." + cpid + "_copy").each(function(index) { tmpVarToDel.push($(this).find("select, input").first().val()); });
				var newVal = "";
				for (var i in tmpVarToDel) {
					if (newVal.length > 0) newVal += ",";
					newVal += tmpVarToDel[i].replace(/\\/g, "\\\\").replace(/,/g, "\\,");
				}
				container.find("." + cpid + "_copy").remove();
				if (originalInput.is("select")) {
					if (sword.DEBUG) console.log("Input is a select");
					$("#" + cpid).remove();
					container.find("form").append("<input name=\"" + cpName + "\" id=\"" + cpid + "\" type=\"hidden\" value=\"\" >");
					$("#" + cpid).val(newVal);
					if (sword.DEBUG) console.log($("#" + cpid).attr("name") + " new value: " + $("#" + cpid).val());
				} else {
					originalInput.val(newVal);
				}
			});
			
			//Compute the Indexer schedule conf
			var indexerConf = { "periods": new Array() };
			container.find("div.ExclPer").each(function(){
				var days = $(this).find(".ExclPerDays").first().val();
				if (days !== "-3") {
					var from = $(this).find(".ExclPerFrom").first().val();
					var _for = $(this).find(".ExclPerFor").first().spinner("value");
					indexerConf.periods.push({ "day": parseInt(days), "start": parseInt(from), "duration": _for });
				}
			});
			container.find(".ExclPerLine").remove();
			container.find("form").append("<input name=\"schedule\" class=\"ScheduleInputComputed\" type=\"hidden\" value=\"\" >");
			container.find(".ScheduleInputComputed").first().val(JSON.stringify(indexerConf));
			
			event.data.isSubmitting = true;
			container.find("form").submit();
		}
	}, addNewIndexerScheduleEntry: function(htmlElem) {
		var elemId = htmlElem.attr("id");
		var idComponents = elemId.match(/ExclPer([0-9]+)_([0-9]+)/);
		if (idComponents) {
			var tabIndex = idComponents[1];
			var entryIndex = idComponents[2];
			var schedEntry = $("#ES_ConEditTabIndexScheduleValues").children().clone();
			schedEntry.each(function(){
				if ($(this).hasClass("EPDays")) {
					$(this).find("select.ExclPerDays").attr("name", "ExclPerDays" + tabIndex + "_" + entryIndex);
					$(this).find("label").attr("for", "ExclPerDays" + tabIndex + "_" + entryIndex);
				} else if ($(this).hasClass("EPStart")) {
					$(this).find("select.ExclPerFrom").attr("name", "ExclPerFrom" + tabIndex + "_" + entryIndex);
					$(this).find("label").attr("for", "ExclPerFrom" + tabIndex + "_" + entryIndex);
				} else if ($(this).hasClass("EPDuration")) {
					$(this).find("input.ExclPerFor").attr("name", "ExclPerFor" + tabIndex + "_" + entryIndex);
					$(this).find("label").attr("for", "ExclPerFor" + tabIndex + "_" + entryIndex);
				}
			});
			htmlElem.html(schedEntry);
			$("select[name=\"ExclPerDays" + tabIndex + "_" + entryIndex + "\"]").val("-3");
			$("input[name=\"ExclPerFor" + tabIndex + "_" + entryIndex + "\"]").spinner({ "min": 1, "disabled": true });
			$("select[name=\"ExclPerFrom" + tabIndex + "_" + entryIndex + "\"]").prop("disabled", true);
		}
	}, updateIndexerScheduleEntry: function(event) {
		var value = $(this).val();
		var container = $(this).parent().parent();
		var elemId = container.attr("id");
		var idComponents = elemId.match(/ExclPer([0-9]+)_([0-9]+)/);
		if (idComponents) {
			var tabIndex = idComponents[1];
			var entryIndex = idComponents[2];
			if (value === "-3") {//If set to "Never"
				//Disable "for" and "from"
				$("input[name=\"ExclPerFor" + tabIndex + "_" + entryIndex + "\"]").spinner( "option", "disabled", true );
				$("select[name=\"ExclPerFrom" + tabIndex + "_" + entryIndex + "\"]").prop("disabled", true);
				
				var allEntries = event.data.container.find(".ExclPer");
				var firstEntryId = allEntries.first().attr("id");
				var lastEntryId = allEntries.last().attr("id");
				if (firstEntryId !== elemId) {//Entry set to "Never" is not the first one -> delete it
					container.parent().remove();
				}

				allEntries = event.data.container.find(".ExclPer");//reload entries list
				lastEntryId = allEntries.last().attr("id");
				if (allEntries.length === 2) //Check if only 2 entries remaining ; both set to never. If so, delete the last
					if ($("#" + lastEntryId).find("select.ExclPerDays").first().val() === "-3") 
						$("#" + lastEntryId).parent().remove();
			} else {
				$("input[name=\"ExclPerFor" + tabIndex + "_" + entryIndex + "\"]").spinner( "option", "disabled", false );
				$("select[name=\"ExclPerFrom" + tabIndex + "_" + entryIndex + "\"]").prop("disabled", false);
				var val = $("input[name=\"ExclPerFor" + tabIndex + "_" + entryIndex + "\"]").spinner( "value" );
				if (val) $("input[name=\"ExclPerFor" + tabIndex + "_" + entryIndex + "\"]").spinner( "value", val );
				else $("input[name=\"ExclPerFor" + tabIndex + "_" + entryIndex + "\"]").spinner( "value", 1 );

				var allEntries = event.data.container.find(".ExclPer");
				var firstEntryId = allEntries.first().attr("id");
				var lastEntryId = allEntries.last().attr("id");
				
				//Check if entry is the last one
				if (lastEntryId === elemId) {//entry is the last one -> add an empty one
					var nextEntryIndex = entryIndex + 1;
					var schedX = $("#ES_ConEditTabIndexScheduleX").children().clone();
					schedX.find("div.ExclPer").attr("id", "ExclPer" + tabIndex + "_" + nextEntryIndex);
					$("#" + event.data.indexerConfigFieldsetId).append(schedX);
					event.data.addNewIndexerScheduleEntry($("#ExclPer" + tabIndex + "_" + nextEntryIndex));
				}
			}
		}
	}, toggleGroupRetrievalConfVisibility: function(event) {
		if ($("#" + event.data).is(":visible")) $("#" + event.data).hide();
		else $("#" + event.data).show();
	}, togglePushConfVisibility: function(event) {
		if ($("#" + event.data).is(":visible")) $("#" + event.data).hide();
		else $("#" + event.data).show();
	}
};

sword.fillConnectorDashboardTab = function(event) {

	var tabRef = sword.addTab("Monitor Connector");
	
	//Refresh tabs
	$("#tabs").tabs("refresh");
	sword.showTab(tabRef);
	
	var connectorId = $(this).parent().parent().attr("id").substring(2);
	
	var cdbm = new sword.DashboardManager(tabRef, connectorId);
	tabRef.tabManager = cdbm;
	cdbm.init();
	
};

sword.DashboardManager = function(tr, connectorId) {
	this.tabIndex = tr.index;
	this.containerId = tr.containerId;
	this.container = tr.container;
	this.connectorId = connectorId;
	this.connectorClass = sword.conf.configuredConnectors[this.connectorId];
	this.connectorDashboardId = "ConnectorDashboard" + this.tabIndex;
	
	this.groupCacheStateId = "GroupCacheState" + this.tabIndex;
	this.groupCacheStateTitleId = "GroupCacheStateTitle" + this.tabIndex;
	this.groupCacheStateContentsId = "GroupCacheStateContents" + this.tabIndex;
	
	this.indexerStateId = "IndexerState" + this.tabIndex;
	this.indexerStateTitleId = "IndexerStateTitle" + this.tabIndex;
	this.indexerStateContentsId = "IndexerStateContents" + this.tabIndex;
	
	this.reloadCacheId = "ReloadCache" + this.tabIndex;
	this.refreshCacheStateId = "RefreshCacheState" + this.tabIndex;
	
	this.idbTopSectionId = "IDBTopSection" + this.tabIndex;
	this.idbStartButtonId = "IDBStartButton" + this.tabIndex;
	this.idbResetButtonId = "IDBResetButton" + this.tabIndex;
	this.idbViewLogButtonId = "ViewIndexerLog" + this.tabIndex;
	this.idbStopButtonId = "IDBStopButton" + this.tabIndex;
	this.idbKillButtonId = "IDBKillButton" + this.tabIndex;
	this.loadIconId = "LoadingIcon" + this.tabIndex;

	this.idbApplyDBChangesButtonId = "IDBApplyDBChangesButton" + this.tabIndex;
	this.idbStartDBBrowsingButtonId = "IDBStartDBBrowsingButton" + this.tabIndex;
	this.idbStopDBBrowsingButtonId = "IDBStopDBBrowsingButton" + this.tabIndex;
};
sword.DashboardManager.prototype = {

	init: function() {
	
		var connectorDef = sword.conf.installedConnectors[this.connectorClass];
		this.container.empty();
	
		this.container.html($("#ES_IndexerDashboardBase").children().clone());
		this.container.find("div.ConnectorDashboard").attr("id", this.connectorDashboardId);
		this.container.find("div.GroupCacheState").attr("id", this.groupCacheStateId);
		this.container.find("div.IndexerState").attr("id", this.indexerStateId);
	
		this.container.append("<span class=\"TabIdRef\" ></span>");
		this.container.find("span.TabIdRef").text("MON_" + this.connectorId);
		sword.setLastActivatedTab("MON_" + this.connectorId);
		
		$("#TabFor" + this.containerId + " > a").html("Monitoring <span style=\"font-style: italic\">#" + this.connectorId + "</span>");
		this.container.find(".HeaderText").html("Monitoring connector <span style=\"font-style: italic\">#" + this.connectorId + "</span>");
	
		var isConfiguredCachableGroupRetriever = (sword.conf.configuredCachableGroupRetrievers[this.connectorId] === true);
		var isConfiguredIndexer = (sword.conf.configuredIndexers[this.connectorId] === true);
		
		sword.hideLoadingSpinner(this.container);
		
		if (isConfiguredCachableGroupRetriever) this.onReloadGroupCacheOK();
		else $("#" + this.groupCacheStateId).remove();
		
		if (isConfiguredIndexer) this.reloadIndexerState();
		else $("#" + this.indexerStateId).remove();
		
		$(document).on("click", "#" + this.reloadCacheId, this, this.reloadGroupCache);
		$(document).on("click", "#" + this.refreshCacheStateId, this, this.reloadGroupCacheState);
		$(document).on("click", "#" + this.idbStartButtonId, this, this.startIndexer);
		$(document).on("click", "#" + this.idbResetButtonId, this, this.resetIndexer);
		$(document).on("click", "#" + this.idbStopButtonId, this, this.stopIndexer);
		$(document).on("click", "#" + this.idbKillButtonId, this, this.killIndexer);
		$(document).on("click", "#" + this.idbViewLogButtonId, this, function(event){ window.open("/SCS/secure/restconf/connector/indexer/log?id=" + encodeURIComponent(event.data.connectorId), "_blank"); });
		$(document).on("click", "#" + this.idbApplyDBChangesButtonId, this, this.applyDBChanges);
		$(document).on("click", "#" + this.idbStartDBBrowsingButtonId, this, this.startDBBrowsing);
		$(document).on("click", "#" + this.idbStopDBBrowsingButtonId, this, this.stopDBBrowsing);
		
	}, close: function() {
		$(document).off("click", "#" + this.reloadCacheId);
		$(document).off("click", "#" + this.refreshCacheStateId);
		$(document).off("click", "#" + this.idbStartButtonId);
		$(document).off("click", "#" + this.idbResetButtonId);
		$(document).off("click", "#" + this.idbStopButtonId);
		$(document).off("click", "#" + this.idbKillButtonId);
		$(document).off("click", "#" + this.idbViewLogButtonId);
		if (this.dashboard) this.dashboard.close.call(this.dashboard);
		if (this.dbBrowser) this.dbBrowser.close.call(this.dbBrowser);
	}, reloadGroupCache: function(event) {
		var _this = event.data;
		sword.showLoadingSpinner(_this.container, "Loading group cache state");
		$.ajax({
			url: "/SCS/secure/restconf/connector/cache/reload", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Reloading failed", "Failed to force group cache reload");
			}, 
			success: _this.onReloadGroupCacheOK, 
			complete: function(){ sword.hideLoadingSpinner(this.container); }
		});
	}, reloadGroupCacheState: function(event) {
		var _this = event.data;
		sword.showLoadingSpinner(_this.container, "Loading group cache state");
		$.ajax({
			url: "/SCS/secure/restconf/connector/cache", 
			type: "GET", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Loading failed", "Failed to load group cache state");
			}, 
			success: _this.onGroupCacheStateOK, 
			complete: function(){ sword.hideLoadingSpinner(this.container); }
		});
	}, onReloadGroupCacheOK: function() {
		var event = {};
		event.data = this;
		this.reloadGroupCacheState(event);
	}, onGroupCacheStateOK: function(gcs, textStatus, jqXHR) {
	
		$("#" + this.groupCacheStateId).html($("#ES_IndexerDashboardGCState").children().clone());
		$("#" + this.groupCacheStateId).find("div.GroupCacheStateTitle").attr("id", this.groupCacheStateTitleId);
		$("#" + this.groupCacheStateId).find("div.GroupCacheStateContents").attr("id", this.groupCacheStateContentsId);
		
		var groupCacheStateContentsElem = $("#" + this.groupCacheStateContentsId);
		groupCacheStateContentsElem.find(".CacheAgeVal").html(gcs.CacheAge);
		groupCacheStateContentsElem.find(".CacheInfoVal").html(gcs.info);
		
		groupCacheStateContentsElem.find("button.ReloadCache").attr("id", this.reloadCacheId);
		$("#" + this.reloadCacheId).button({ icons: { primary: "ui-icon-circle-triangle-e" }, text: true });
		
		groupCacheStateContentsElem.find("button.RefreshCacheState").attr("id", this.refreshCacheStateId);
		$("#" + this.refreshCacheStateId).button({ icons: { primary: "ui-icon-refresh" }, text: true });
		
		if (gcs.IsLoading) {
			$("#" + this.reloadCacheId).text("Reload in progress");
			$("#" + this.reloadCacheId).button("refresh");
			$("#" + this.reloadCacheId).button("disable");
		}
		if (gcs.HasNoCache || gcs.IsFailed) {
			$("#" + this.reloadCacheId).text("Load cache");
			$("#" + this.reloadCacheId).button("refresh");
		}
	}, reloadIndexerState: function() {
		sword.showLoadingSpinner(this.container, "Loading indexer state");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/state", 
			type: "GET", 
			data: { "id": this.connectorId }, 
			context: this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Loading failed", "Failed to load indexer state");
			}, 
			success: this.onIndexerStateOK, 
			complete: function(){ sword.hideLoadingSpinner(this.container); }
		});
	}, onIndexerStateOK: function(is, textStatus, jqXHR) {
		$("#" + this.indexerStateId).html($("#ES_IndexerDashboardIndexerState").children().clone());
		$("#" + this.indexerStateId).find("div.IndexerStateTitle").attr("id", this.indexerStateTitleId);
		$("#" + this.indexerStateId).find("div.IndexerStateContents").attr("id", this.indexerStateContentsId);
		
		if (is.IsStarted && is.IsConnected) {
			if (is.IsDBBrowsingMode) {
				$("#" + this.indexerStateContentsId).html($("#ES_IndexerDashboardIndexerStateDBBrowsing").children().clone());
				$("#" + this.indexerStateContentsId).find("div.IDBTopSection").attr("id", this.idbTopSectionId);
				$("#" + this.indexerStateContentsId).find("button.IDBApplyDBChangesBtn").attr("id", this.idbApplyDBChangesButtonId);
				$("#" + this.indexerStateContentsId).find("button.IDBStopDBBBtn").attr("id", this.idbStopDBBrowsingButtonId);
				this.dbBrowser = new sword.indexer.DBBrowser($("#" + this.indexerStateContentsId), this.connectorId);
				this.dbBrowser.init();
			} else {
				$("#" + this.indexerStateContentsId).html($("#ES_IndexerDashboardRunningIndexerState").children().clone());
				$("#" + this.indexerStateContentsId).find("div.IDBTopSection").attr("id", this.idbTopSectionId);
				$("#" + this.indexerStateContentsId).find("button.IDBStopBtn").attr("id", this.idbStopButtonId);
				$("#" + this.indexerStateContentsId).find("button.IDBKillBtn").attr("id", this.idbKillButtonId);
				$("#" + this.indexerStateContentsId).find("button.IDBViewLogBtn").attr("id", this.idbViewLogButtonId);
				
				$.ajax({
					url: "/SCS/secure/restconf/connector/indexer/stats", 
					type: "GET", 
					data: { "id": this.connectorId }, 
					context: this, 
					error: function( jqXHR, textStatus, errorThrown ) {
						sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Loading failed", "Failed to load indexer statistics");
					}, 
					success: this.onIndexerStatsLoadOK
				});
			}
		} else {
			$("#" + this.indexerStateContentsId).html($("#ES_IndexerDashboardStoppedIndexerState").children().clone());
			$("#" + this.indexerStateContentsId).find("div.IDBTopSection").attr("id", this.idbTopSectionId);
			$("#" + this.indexerStateContentsId).find("button.IDBStartBtn").attr("id", this.idbStartButtonId);
			$("#" + this.indexerStateContentsId).find("button.IDBResetBtn").attr("id", this.idbResetButtonId);
			$("#" + this.indexerStateContentsId).find("button.IDBViewLogBtn").attr("id", this.idbViewLogButtonId);
			$("#" + this.indexerStateContentsId).find("button.IDBStartDBBBtn").attr("id", this.idbStartDBBrowsingButtonId);

			$.ajax({
				url: "/SCS/secure/restconf/connector/indexer/LastKnownStats", 
				type: "GET", 
				data: { "id": this.connectorId }, 
				context: this, 
				success: this.onIndexerLastKnownStatsLoadOK
			});
		}
		$("#" + this.indexerStateId).find("button.IDBApplyDBChangesBtn, button.IDBStartBtn").button({ icons: { primary: "ui-icon-play" }, text: true });
		$("#" + this.indexerStateId).find("button.IDBStopDBBBtn, button.IDBStopBtn").button({ icons: { primary: "ui-icon-stop" }, text: true });
		$("#" + this.indexerStateId).find("button.IDBKillBtn").button({ icons: { primary: "ui-icon-power" }, text: true });
		$("#" + this.indexerStateId).find("button.IDBViewLogBtn, button.IDBStartDBBBtn").button({ icons: { primary: "ui-icon-circle-zoomin" }, text: true });
		$("#" + this.indexerStateId).find("button.IDBResetBtn").button({ icons: { primary: "ui-icon-alert" }, text: true });
	}, startIndexer: function(event) {
		var _this = event.data;
		_this.disableButtons.call(_this);
		$("#" + _this.idbTopSectionId).find("tr").first().append("<td><span class=\"LoadingIcon\" id=\"" + _this.loadIconId + "\" ></span></td>");
		
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/start", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Start failed", "Failed to start indexer");
			}, 
			success: _this.onIndexerStartOK, 
			complete: function(){ $("#" + this.loadIconId).parent().remove(); }
		});
	}, onIndexerStatsLoadOK: function(stats, textStatus, jqXHR) {
		this.dashboard = new sword.indexer.Dashboard($("#" + this.indexerStateContentsId), this.connectorId, this.reloadIndexerState, this, true);
		this.dashboard.init(stats);
	}, onIndexerLastKnownStatsLoadOK: function(stats, textStatus, jqXHR) {
		var wrappedStats = {};
		wrappedStats.Statistics = stats;
		wrappedStats.Threads = [];
		this.dashboard = new sword.indexer.Dashboard($("#" + this.indexerStateContentsId), this.connectorId, this.reloadIndexerState, this, false);
		this.dashboard.init(wrappedStats);
	}, resetIndexer: function(event) {
			var _this = event.data;
			var html = (sword.conf.CurrentUser === "SCSAdmin") ? $("#ES_IndexerDashboardResetAlert1").children().clone() : $("#ES_IndexerDashboardResetAlert2").children().clone();
			html.attr("id", "dialog");
			$("body").append(html);
			$("#dialog").dialog({
				resizable: false, 
				height: "auto", 
				title: "Are you sure?", 
				modal: true, 
				close: function( event, ui ) { $( "#dialog" ).remove(); }, 
				buttons: [
					{ 
						text: "Confirm", 
						click: function() {
							var cb = $("#dialog").find("input[name=\"ResetGsaDatasource\"]");
							var deleteDatasource = false;
							if (cb.length > 0) deleteDatasource = cb.is(":checked");
							$(this).dialog("close");
							_this.doResetIndexer.call(_this, deleteDatasource);
						}
					}, { 
						text: "Cancel", 
						click: function() {
							$(this).dialog("close");
						}
					}
				]
			});
	}, doResetIndexer: function(deleteDatasource) {
		this.disableButtons();
		$("#" + this.idbTopSectionId).find("tr").first().append("<td><span class=\"LoadingIcon\" id=\"" + this.loadIconId + "\" ></span></td>");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/reset", 
			type: "POST", 
			data: { "id": this.connectorId, "deleteDatasource": deleteDatasource }, 
			context: this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Reset failed", "Failed to reset indexer");
			}, 
			success: this.onIndexerStartOK, 
			complete: function(){ $("#" + this.loadIconId).parent().remove(); }
		});
	}, onIndexerStartOK: function(data, textStatus, jqXHR) {
		if (data && data.ERROR) {
			$("body").append("<div id=\"dialog\" title=\"Start failed\"><p><span class=\"ui-icon ui-icon-alert\" style=\"float:left; margin:0 7px 20px 0;\"></span>Failed to start indexer<br/>Error: " + data.ERROR + "</p></div>");
			$("#dialog").dialog({
				resizable: false, 
				height:200, 
				modal: true, 
				close: function( event, ui ) { $( "#dialog" ).remove(); }, 
				buttons: {
					Ok: function() { $(this).dialog("close"); }
				}
			});
		} else {
			this.reloadIndexerState();
		}
	}, stopIndexer: function(event) {
		var _this = event.data;
		_this.disableButtons.call(_this);
		$("#" + _this.idbTopSectionId).find("tr").first().append("<td><span class=\"LoadingIcon\" id=\"" + _this.loadIconId + "\" ></span></td>");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/stop", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Stop failed", "Failed to stop indexer");
			}, 
			success: _this.onReloadStateReady, 
			complete: function(){ $("#" + this.loadIconId).parent().remove(); }
		});
	}, killIndexer: function(event) {
		var _this = event.data;
		_this.disableButtons.call(_this);
		$("#" + _this.idbTopSectionId).find("tr").first().append("<td><span class=\"LoadingIcon\" id=\"" + _this.loadIconId + "\" ></span></td>");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/kill", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "Stop failed", "Failed to stop indexer");
			}, 
			success: _this.onReloadStateReady, 
			complete: function(){ $("#" + this.loadIconId).parent().remove(); }
		});
	}, onReloadStateReady: function(is, textStatus, jqXHR) {
		this.reloadIndexerState();
	}, applyDBChanges: function(event) {
		var _this = event.data;
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/applydbchanges", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			success: _this.onReloadStateReady
		});
	}, startDBBrowsing: function(event) {
		var _this = event.data;
		_this.disableButtons.call(_this);
		$("#" + _this.idbTopSectionId).find("tr").first().append("<td><span class=\"LoadingIcon\" id=\"" + _this.loadIconId + "\" ></span></td>");
		
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/startdbbrowsing", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			success: _this.onReloadStateReady, 
			complete: function(){ $("#" + this.loadIconId).parent().remove(); }
		});
	}, stopDBBrowsing: function(event) {
		var _this = event.data;
		_this.disableButtons.call(_this);
		$("#" + _this.idbTopSectionId).find("tr").first().append("<td><span class=\"LoadingIcon\" id=\"" + _this.loadIconId + "\" ></span></td>");
		
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/stopdbbrowsing", 
			type: "POST", 
			data: { "id": _this.connectorId }, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			success: _this.onReloadStateReady, 
			complete: function(){
				$("#" + this.loadIconId).parent().remove();
				if (this.dbBrowser) delete this.dbBrowser;
			}
		});
		
		
	}, disableButtons: function() {
		var allButtons = $("#" + this.indexerStateContentsId).find("button.IDBApplyDBChangesBtn, button.IDBStopDBBBtn, button.IDBStopBtn, button.IDBKillBtn, button.IDBViewLogBtn, button.IDBStartBtn, button.IDBResetBtn, button.IDBStartDBBBtn");
		allButtons.button("disable");
		allButtons.css("cursor", "progress").attr("disabled", "disabled");
	}

};

$(document).ready(sword.init);