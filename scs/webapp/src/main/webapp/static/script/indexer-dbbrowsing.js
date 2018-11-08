if ((typeof sword) === "undefined") window["sword"] = {};
if ((typeof sword.indexer) === "undefined") sword.indexer = {};

sword.indexer.DBBrowser = function(elem, cid) {
	this.HtmlElem = elem;
	this.ConnectorId = cid;
	this.pageSize = 25;
};

sword.indexer.DBBrowser.prototype = {
	init: function() {
	
		$(document).on("click", ".DBBDirId", this, this.fetchDirChildren);
		$(document).on("click", ".DBBNavElem", this, this.navToParent);
		$(document).on("click", ".DBBReindex", this, this.reindexNode);
		$(document).on("click", ".DBBExclude", this, this.excludeNode);
		$(document).on("click", ".DBBSearchBtn", this, this.searchChild);
		$(document).on("click", ".DBBStateFilterBtn", this, this.filterByState);
	
		this.HtmlElem.append("<div class=\"DBBTree\" id=\"DBBTree" + this.ConnectorId + "\" ><div class=\"DBBTreeLegend\" ><table></table></div><div class=\"DBBTreeContents\" ></div></div>");
		var legend = $("#DBBTree" + this.ConnectorId).find(".DBBTreeLegend>table");
		
		legend
			.append("<tr><td colspan=\"2\" class=\"DBBLegendTitle\" >Item types</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-yellow ui-icon-folder-collapsed\"></span></td><td>Folder</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-yellow ui-icon-document\"></span></td><td>Document</td></tr>");
		legend.append("<tr><td colspan=\"2\" class=\"DBBLegendEmptyRow\" ></td></tr>");
		legend
			.append("<tr><td colspan=\"2\" class=\"DBBLegendTitle\" >Indexing states</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-green ui-icon-check\"></span></td><td>Complete</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-clock\"></span></td><td>Pending</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-red ui-icon-closethick\"></span></td><td>Excluded</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-red ui-icon-notice\"></span></td><td>Re-exploration needed due to errors during last traversal</td></tr>")
			.append("<tr><td><span class=\"ui-icon ui-icon-ib ui-icon-calendar\"></span></td><td>Document last indexing date</td></tr>");
		legend.append("<tr><td colspan=\"2\" class=\"DBBLegendEmptyRow\" ></td></tr>");
		legend
			.append("<tr><td colspan=\"2\" class=\"DBBLegendTitle\" >Action buttons</td></tr>")
			.append("<tr><td><span class=\"DBBBtn DBBReindex\"></span></td><td>Request folder re-exploration</td></tr>")
			.append("<tr><td><span class=\"DBBBtn DBBReindex\"></span></td><td>Request document re-indexing</td></tr>")
			.append("<tr><td><span class=\"DBBBtn DBBExclude\"></span></td><td>Exclude item from indexing scope</td></tr>");
		$(".DBBReindex").button({ icons: { secondary: "ui-icon-refresh" }, text: false });
		$(".DBBExclude").button({ icons: { secondary: "ui-icon-closethick" }, text: false });
		
		this.fetchChildren(null, 0);
		
	}, close: function() {
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/stopdbbrowsing", 
			type: "POST", 
			data: { "id": this.ConnectorId }, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			async: false
		});
	}, getTreeContentsElem: function() {
		return $("#DBBTree" + this.ConnectorId).find("div.DBBTreeContents").first();
	}, fetchDirChildren: function(event) {
		var _this = event.data;
		_this.fetchChildren.call(_this, $(this).text(), 0);
	}, navToParent: function(event) {
		var _this = event.data;
		var pid = null;
		if (!$(this).hasClass("DBBNavRootEl")) pid = $(this).text();
		_this.fetchChildren.call(_this, pid, 0);
	}, navToPage: function(event) {
		var _this = event.data.that;
		var states = event.data.states;
		var curParent = _this.getTreeContentsElem.call(_this).find("span.DBBNavSelectedParent");
		var isRoot = curParent.hasClass("DBBNavRootEl");
		var pid = isRoot ? null : curParent.text().replace(/ \([0-9]+ children\)/, "");
		_this.fetchChildren.call(_this, pid, parseInt($(this).text()) - 1, states);
	}, fetchChildren: function(parentId, pageNum, states) {
		//console.log("Fetching children of " + parentId + " (page: " + pageNum + ")");
		var getData = { "id": this.ConnectorId, "num": this.pageSize.toString() };
		if ((typeof parentId) === "string") getData["parent"] = parentId;
		if ((typeof pageNum) === "number") getData["page"] = pageNum.toString();
		if ((typeof states) === "string") getData["states"] = states;
		sword.showLoadingSpinner(this.HtmlElem.closest(".TabContainer"), "Loading children");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/internaldb/childnodes", 
			type: "GET", 
			data: getData, 
			context: this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			success: this.displayChildren
		});
	}, displayChildren: function(resp, textStatus, jqXHR) {
		sword.hideLoadingSpinner(this.HtmlElem.closest(".TabContainer"));
		if ((typeof resp.ChildrenCount) === "number") {
		
			var isNotRoot = resp.hasOwnProperty("Parents");
			var currentPage = resp.page;
			var treeContents = this.getTreeContentsElem();
			treeContents.empty();
			treeContents.append("<div class=\"DBBNav\" ></div>");
			var navContainer = treeContents.find("div.DBBNav");
			if (isNotRoot) {
				navContainer.append("<span class=\"DBBNavElem DBBNavRootEl\" >root</span>");
				for (var i=resp.Parents.length; i>1; i--) 
					navContainer.append("<span class=\"DBBNavSep\" >&gt;</span><span class=\"DBBNavElem\" >" + resp.Parents[i-1] + "</span>");
				navContainer.append("<span class=\"DBBNavSep\" >&gt;</span><span class=\"DBBNavSelectedParent\" >" + resp.Parents[0] + " (" + resp.ChildrenCount + " children)</span>");
			} else {
				navContainer.append("<span class=\"DBBNavSelectedParent DBBNavRootEl\" >root (" + resp.ChildrenCount + " children)</span>");
			}
			
			var states = null;
			if (resp.ChildrenCount > this.pageSize) {
			
				treeContents.append($("#ES_DBBFilters").children().clone());
				$(".DBBSearchBtn").button({ icons: { secondary: "ui-icon-search" }, text: false });
				$(".DBBStateFilterBtn").button({ icons: { secondary: "ui-icon-arrowthickstop-1-e" }, text: false });
				
				if (resp.hasOwnProperty("state")) {
					var statesArr = resp.state.split(",");
					states = "";
					for (var i in statesArr) {
						if (states.length > 0) states += ",";
						states += statesArr[i];
						treeContents.find("input[name=\"DBBStateFilterState\"][value=\"" + statesArr[i] + "\"]").prop("checked", true);
					}
				}
			} else if (resp.hasOwnProperty("state")) {
				treeContents.append($("#ES_DBBFilters").children().clone());
				treeContents.find("td.DBBSearchLbl").parent().remove();
				statesArr = resp.state.split(",");
				states = "";
				for (var i in statesArr) {
					if (states.length > 0) states += ",";
					states += statesArr[i];
					treeContents.find("input[name=\"DBBStateFilterState\"][value=\"" + statesArr[i] + "\"]").prop("checked", true);
				}
			}
			
			var child = null;
			var childHexId = null;
			var childElem = null;
			for (var i in resp.children) {
				child = resp.children[i];
				childHexId = this.toHexId(child.i);
				treeContents.append("<div class=\"DBBNode\" id=\"DBBNode" + child.t + childHexId + "\" ></div>");
				childElem = $("#DBBNode" + child.t + childHexId);
				var isDir = child.t === "f";
				if (isDir) childElem.append("<span style=\"white-space: nowrap\" ><span class=\"ui-icon ui-icon-ib ui-icon-yellow ui-icon-folder-collapsed DBBTypeIcon\"></span><span class=\"DBBDirId DBBId\"></span></span>");
				else childElem.append("<span style=\"white-space: nowrap\" ><span class=\"ui-icon ui-icon-ib ui-icon-yellow ui-icon-document DBBTypeIcon\"></span><span class=\"DBBDocId DBBId\"></span></span>");
				childElem.find(".DBBId").text(child.i);

				if (child.s === 3) {//NEW
					childElem.append("<span class=\"ui-icon ui-icon-ib ui-icon-clock\" ></span>");
				} else if (child.s === 2) {//COMPLETE
					childElem.append("<span class=\"ui-icon ui-icon-ib ui-icon-green ui-icon-check\" ></span>");
				} else if (child.s === 1) {//EXCLUDED
					childElem.append("<span class=\"ui-icon ui-icon-ib ui-icon-red ui-icon-closethick\" ></span>");
				} else {
					childElem.append("<span class=\"ui-icon ui-icon-ib ui-icon-red ui-icon-notice\" ></span>");
				}
				
				childElem.append("<span class=\"DBBBtn DBBReindex\"></span>");
				childElem.append("<span class=\"DBBBtn DBBExclude\"></span>");
				
				if (child.d) childElem.append("<span class=\"ui-icon ui-icon-ib ui-icon-calendar\" ></span><span class=\"DBBDate\">" + new Date(child.d).toLocaleString() + "</span>");
				
				if (resp.SearchedFor && (resp.SearchedFor === child.i)) {
					childElem.find(".DBBId").css("color", "red");
					treeContents.find("input.DBBSearchInput").val(child.i);
				}
			}
			$(".DBBReindex").button({ icons: { secondary: "ui-icon-refresh" }, text: false });
			$(".DBBExclude").button({ icons: { secondary: "ui-icon-closethick" }, text: false });
			
			if (resp.ChildrenCount > this.pageSize) {
				this.buildPagingLinks(resp.ChildrenCount, currentPage, states);
			}
			
		} else {
			//TODO notify error
		}
	}, reindexNode: function(event) {
		var _this = event.data;
		_this.reloadNodeState.call(_this, $(this).parent(), "reindex");
	}, excludeNode: function(event) {
		var _this = event.data;
		_this.reloadNodeState.call(_this, $(this).parent(), "exclude");
	}, reloadNodeState: function(parentNode, action) {
		if (parentNode.hasClass("DBBNode")) {
			var n = parentNode.find("span.DBBId");
			sword.showLoadingSpinner(this.HtmlElem.closest(".TabContainer"), "Updating node state");
			$.ajax({
				url: "/SCS/secure/restconf/connector/indexer/internaldb/updatestate", 
				type: "GET", 
				data: { "id": this.ConnectorId, "nid": n.text(), "ntype": (n.hasClass("DBBDirId") ? "dir" : "doc"), "action": action }, 
				context: this, 
				error: function( jqXHR, textStatus, errorThrown ) {
					sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
				}, 
				success: this.displayChildren
			});
		}
	}, searchChild: function(event) {
		var _this = event.data;
		var curParent = _this.getTreeContentsElem.call(_this).find("span.DBBNavSelectedParent");
		var isRoot = curParent.hasClass("DBBNavRootEl");
		var getData = { "id": _this.ConnectorId, "nid": $(this).parent().parent().find("input.DBBSearchInput").val() };
		if (!isRoot) getData["parent"] = curParent.text().replace(/ \([0-9]+ children\)/, "");
		sword.showLoadingSpinner(_this.HtmlElem.closest(".TabContainer"), "Searching child node");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/internaldb/search", 
			type: "GET", 
			data: getData, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			success: _this.displayChildren
		});
	}, filterByState: function(event) {
		var _this = event.data;
		var curParent = _this.getTreeContentsElem.call(_this).find("span.DBBNavSelectedParent");
		var isRoot = curParent.hasClass("DBBNavRootEl");
		var getData = { "id": _this.ConnectorId };
		if (!isRoot) getData["parent"] = curParent.text().replace(/ \([0-9]+ children\)/, "");
		var states = "";
		_this.getTreeContentsElem.call(_this).find("input[name=\"DBBStateFilterState\"]:checked").each(function(){
			if (states.length > 0) states += ",";
			states += $(this).val();
		});
		if (states.length > 0) getData["states"] = states;
		sword.showLoadingSpinner(_this.HtmlElem.closest(".TabContainer"), "Filtering by state");
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/internaldb/childnodes", 
			type: "GET", 
			data: getData, 
			context: _this, 
			error: function( jqXHR, textStatus, errorThrown ) {
				sword.stdErrorAlert(jqXHR, textStatus, errorThrown, "DB Browsing error", "An error occurred while consulting the indexer internal state");
			}, 
			success: _this.displayChildren
		});
	}, toHexId: function(strId) {
		var l = strId.length;
		var hexId = "";
		for (var i=0; i<l; i++) hexId += strId.charCodeAt(i).toString(16);
		return hexId;
	}, buildPagingLinks: function(numChildren, currentPage, states) {
		var treeContents = this.getTreeContentsElem();
		treeContents.find("div.DBBNodePaging").remove();

		var numPages = parseInt((numChildren - 1) / this.pageSize) + 1;
		var maxPageIndex = numPages - 1;
		if (sword.DEBUG) console.log("numPages: " + numPages + "; currentPage: " + currentPage);
		var minPage = currentPage - 4;
		var missings = 0;
		if (minPage < 0) {
			missings = -minPage;
			minPage = 0;
		}
		var maxPage = currentPage + 4 + missings;
		if (maxPage > maxPageIndex) {
			missings = maxPage - maxPageIndex;
			maxPage = maxPageIndex;
			minPage -= missings;
			if (minPage < 0) minPage = 0;
		}
		
		$(document).off("click", ".DBBPageLink");
		treeContents.append("<div class=\"DBBNodePaging\" ></div>");
		var pagingElem = treeContents.find("div.DBBNodePaging");
		for (var i=minPage; i<=maxPage; i++) {
			if (i === currentPage) pagingElem.append("<span class=\"DBBSelectedPage\" >" + (i+1) + "</span>");
			else pagingElem.append("<span class=\"DBBPageLink\" >" + (i+1) + "</span>");
		}
		$(document).on("click", ".DBBPageLink", { "that": this, "states": states }, this.navToPage);
		
	}
};