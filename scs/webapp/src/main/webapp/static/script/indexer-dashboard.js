if ((typeof sword) === "undefined") window["sword"] = {};
if ((typeof sword.indexer) === "undefined") sword.indexer = {};

sword.indexer.Dashboard = function(elem, cid, callback, callbackContext, isActive) {

	this.HtmlElem = elem;
	this.ConnectorId = cid;
	this.callback = callback;
	this.callbackContext = callbackContext;
	this.isActive = isActive;
	
};

sword.indexer.Dashboard.prototype = {
	init: function(statsData) {

		this.updatePushInfoHTML(statsData);
		this.generateEmptyCharts();
		
		
		$("#IDBCharts" + this.ConnectorId).hide();
		$("#IDBThreads" + this.ConnectorId).hide();
		if (this.isActive) {
			$("#IDBThreads" + this.ConnectorId).show();
			$("#IDBThreads" + this.ConnectorId).find(".IDBSectionContents").first().html(this.getThreadInfoHTML(statsData));
			this.HtmlElem.find(".IDBTStack").hide();
			this.HtmlElem.on("click", ".IDBTTitle", this.ToggleStack);
			this.ReloadStats(this);
			this.ReloadGraph(this);
			this.reloadStatsId = setInterval(this.ReloadStats, 10000, this);//Every 10 seconds
			this.reloadGraphId = setInterval(this.ReloadGraph, 60000, this);//Every minute
		} else {
			$.ajax({
				url: "/SCS/secure/restconf/connector/indexer/LastKnownHistory", 
				type: "GET", 
				data: { "id": this.ConnectorId }, 
				context: this, 
				success: this.UpdateGraph
			});
		}
		
	}, close: function() {
		if (this.reloadStatsId) clearInterval(this.reloadStatsId);
		if (this.reloadGraphId) clearInterval(this.reloadGraphId);
		this.HtmlElem.off("click", ".IDBTTitle");
	}, updatePushInfoHTML: function(statsData) {

		var stats = statsData.Statistics;
		if ((typeof stats) === "undefined") stats = { };
		
		if (!stats.hasOwnProperty("Now")) stats["Now"] = -1;
		if (!stats.hasOwnProperty("DocumentsFound")) stats["DocumentsFound"] = "N/A";
		if (!stats.hasOwnProperty("DocumentsAddedToFeedForIndexing")) stats["DocumentsAddedToFeedForIndexing"] = "N/A";
		if (!stats.hasOwnProperty("ContentBytesAddedToFeed")) stats["ContentBytesAddedToFeed"] = -1;
		if (!stats.hasOwnProperty("BytesActuallySentToGsa")) stats["BytesActuallySentToGsa"] = -1;
		if (!stats.hasOwnProperty("StartTime")) stats["StartTime"] = -1;
		if (!stats.hasOwnProperty("StartDate")) stats["StartDate"] = "N/A";
		if (!stats.hasOwnProperty("Mode")) stats["Mode"] = "Unknown";
		if (!stats.hasOwnProperty("AuditEvents")) stats["AuditEvents"] = false;
		if (!stats.hasOwnProperty("EndTime")) stats["EndTime"] = 1;
		if (!stats.hasOwnProperty("EndDate")) stats["EndDate"] = "N/A";
		if (!stats.hasOwnProperty("AuditEventsProcessed")) stats["AuditEventsProcessed"] = "N/A";
		if (!stats.hasOwnProperty("NodesExplored")) stats["NodesExplored"] = "N/A";
		if (!stats.hasOwnProperty("NodesExcluded")) stats["NodesExcluded"] = "N/A";
		if (!stats.hasOwnProperty("AclAddedToFeedForIndexing")) stats["AclAddedToFeedForIndexing"] = "N/A";
		if (!stats.hasOwnProperty("AclSkippedAsUnmodified")) stats["AclSkippedAsUnmodified"] = "N/A";
		if (!stats.hasOwnProperty("DocumentsExcluded")) stats["DocumentsExcluded"] = "N/A";
		if (!stats.hasOwnProperty("DocumentsAddedToFeedForDeletion")) stats["DocumentsAddedToFeedForDeletion"] = "N/A";
		if (!stats.hasOwnProperty("DocumentsSkippedAsUnmodified")) stats["DocumentsSkippedAsUnmodified"] = "N/A";
		if (!stats.hasOwnProperty("ExplorationErrors")) stats["ExplorationErrors"] = "N/A";
		if (!stats.hasOwnProperty("IndexingErrors")) stats["IndexingErrors"] = "N/A";
		if (!stats.hasOwnProperty("IndexingProcessAborted")) stats["IndexingProcessAborted"] = "N/A";
		if (!stats.hasOwnProperty("ContentBytesAddedToFeedString")) stats["ContentBytesAddedToFeedString"] = "N/A";
		if (!stats.hasOwnProperty("BytesActuallySentToGsaString")) stats["BytesActuallySentToGsaString"] = "N/A";
		
		if ($("#IDBCurStats" + this.ConnectorId).length === 0) {
			this.HtmlElem.append($("#ES_IndexerDashboard").children().clone());
			this.HtmlElem.find("div.IDBCurStats").attr("id", "IDBCurStats" + this.ConnectorId);
			this.HtmlElem.find("div.IDBCurStatsContents").attr("id", "IDBCurStatsContents" + this.ConnectorId);
			this.HtmlElem.find("div.IDBDates").attr("id", "IDBDates" + this.ConnectorId);
			this.HtmlElem.find("td.IDBStartDate").attr("id", "IDBStartDate" + this.ConnectorId);
			this.HtmlElem.find("td.IDBEndDate").attr("id", "IDBEndDate" + this.ConnectorId);
			this.HtmlElem.find("div.IDBEvents").attr("id", "IDBEvents" + this.ConnectorId);
			this.HtmlElem.find("td.IDBEventCounts").attr("id", "IDBEventCounts" + this.ConnectorId);
			this.HtmlElem.find("div.IDBNodesCounts").attr("id", "IDBNodesCounts" + this.ConnectorId);
			this.HtmlElem.find("td.IDBNodesExplored").attr("id", "IDBNodesExplored" + this.ConnectorId);
			this.HtmlElem.find("td.IDBNodesExcluded").attr("id", "IDBNodesExcluded" + this.ConnectorId);
			this.HtmlElem.find("div.IDBACLCounts").attr("id", "IDBACLCounts" + this.ConnectorId);
			this.HtmlElem.find("td.IDBAclAddedToFeedForIndexing").attr("id", "IDBAclAddedToFeedForIndexing" + this.ConnectorId);
			this.HtmlElem.find("td.IDBAclSkippedAsUnmodified").attr("id", "IDBAclSkippedAsUnmodified" + this.ConnectorId);
			this.HtmlElem.find("div.IDBDocsCounts").attr("id", "IDBDocsCounts" + this.ConnectorId);
			this.HtmlElem.find("td.IDBDocumentsFound").attr("id", "IDBDocumentsFound" + this.ConnectorId);
			this.HtmlElem.find("td.IDBDocumentsAddedToFeedForIndexing").attr("id", "IDBDocumentsAddedToFeedForIndexing" + this.ConnectorId);
			this.HtmlElem.find("td.IDBDocumentsSkippedAsUnmodified").attr("id", "IDBDocumentsSkippedAsUnmodified" + this.ConnectorId);
			this.HtmlElem.find("td.IDBDocumentsAddedToFeedForDeletion").attr("id", "IDBDocumentsAddedToFeedForDeletion" + this.ConnectorId);
			this.HtmlElem.find("td.IDBDocumentsExcluded").attr("id", "IDBDocumentsExcluded" + this.ConnectorId);
			this.HtmlElem.find("div.IDBErrCounts").attr("id", "IDBErrCounts" + this.ConnectorId);
			this.HtmlElem.find("td.IDBExplorationErrors").attr("id", "IDBExplorationErrors" + this.ConnectorId);
			this.HtmlElem.find("td.IDBIndexingErrors").attr("id", "IDBIndexingErrors" + this.ConnectorId);
			this.HtmlElem.find("td.IDBIndexingProcessAborted").attr("id", "IDBIndexingProcessAborted" + this.ConnectorId);
			this.HtmlElem.find("div.IDBThroughput").attr("id", "IDBThroughput" + this.ConnectorId);
			this.HtmlElem.find("td.IDBdownloaded").attr("id", "IDBdownloaded" + this.ConnectorId);
			this.HtmlElem.find("td.IDBThroughputD").attr("id", "IDBThroughputD" + this.ConnectorId);
			this.HtmlElem.find("td.IDBuploaded").attr("id", "IDBuploaded" + this.ConnectorId);
			this.HtmlElem.find("td.IDBThroughputU").attr("id", "IDBThroughputU" + this.ConnectorId);
			this.HtmlElem.find("div.IDBCharts").attr("id", "IDBCharts" + this.ConnectorId);
			this.HtmlElem.find("div.IDBDocCountChart").attr("id", "IDBDocCountChart" + this.ConnectorId);
			this.HtmlElem.find("div.IDBBytesCountChart").attr("id", "IDBBytesCountChart" + this.ConnectorId);
			this.HtmlElem.find("div.IDBThreads").attr("id", "IDBThreads" + this.ConnectorId);
		}
		
		$("#IDBCurStats" + this.ConnectorId).find(".IDBSectionTit").first().text((this.isActive ? "Statistics" : "Statistics for last run") + " - mode: " + stats.Mode + ":");
		$("#IDBStartDate" + this.ConnectorId).text(stats.StartDate);
		if (stats.EndTime > 0) $("#IDBEndDate" + this.ConnectorId).css("font-style", "normal").text(stats.EndDate);
		else $("#IDBEndDate" + this.ConnectorId).css("font-style", "italic").text("N/A (still in progress)");
		
		if (stats.AuditEvents) {
			$("#IDBEventCounts" + this.ConnectorId).text(stats.AuditEventsProcessed);
			$("#IDBEvents" + this.ConnectorId).show();
		} else {
			$("#IDBEvents" + this.ConnectorId).hide();
		}
		
		$("#IDBNodesExplored" + this.ConnectorId).text(stats.NodesExplored);
		$("#IDBNodesExcluded" + this.ConnectorId).text(stats.NodesExcluded);
		
		$("#IDBAclAddedToFeedForIndexing" + this.ConnectorId).text(stats.AclAddedToFeedForIndexing);
		$("#IDBAclSkippedAsUnmodified" + this.ConnectorId).text(stats.AclSkippedAsUnmodified);
		
		$("#IDBDocumentsFound" + this.ConnectorId).text(stats.DocumentsFound);
		$("#IDBDocumentsAddedToFeedForIndexing" + this.ConnectorId).text(stats.DocumentsAddedToFeedForIndexing);
		$("#IDBDocumentsSkippedAsUnmodified" + this.ConnectorId).text(stats.DocumentsSkippedAsUnmodified);
		$("#IDBDocumentsAddedToFeedForDeletion" + this.ConnectorId).text(stats.DocumentsAddedToFeedForDeletion);
		$("#IDBDocumentsExcluded" + this.ConnectorId).text(stats.DocumentsExcluded);
		
		$("#IDBExplorationErrors" + this.ConnectorId).text(stats.ExplorationErrors);
		$("#IDBIndexingErrors" + this.ConnectorId).text(stats.IndexingErrors);
		$("#IDBIndexingProcessAborted" + this.ConnectorId).text(stats.IndexingProcessAborted);
		
		if (stats.ContentBytesAddedToFeed > 0) {
			$("#IDBdownloaded" + this.ConnectorId).css("font-style", "normal").text(stats.ContentBytesAddedToFeedString);
			var refTime = (stats.EndTime > 0) ? stats.EndTime : stats.Now;
			$("#IDBThroughputD" + this.ConnectorId).css("font-style", "normal").html(((stats.ContentBytesAddedToFeed / (refTime - stats.StartTime))).toFixed(3) + " kB.S<span class=\"superscript\" >-1</span>");
		} else {
			$("#IDBdownloaded" + this.ConnectorId).css("font-style", "italic").text("N/A");
			$("#IDBThroughputD").css("font-style", "italic").text("N/A");
		}
		if (stats.BytesActuallySentToGsa > 0) {
			$("#IDBuploaded" + this.ConnectorId).css("font-style", "normal").text(stats.BytesActuallySentToGsaString);
			var refTime = (stats.EndTime > 0) ? stats.EndTime : stats.Now;
			$("#IDBThroughputU" + this.ConnectorId).css("font-style", "normal").html(((stats.BytesActuallySentToGsa / (refTime - stats.StartTime))).toFixed(3) + " kB.S<span class=\"superscript\" >-1</span>");
		} else {
			$("#IDBuploaded" + this.ConnectorId).css("font-style", "italic").text("N/A");
			$("#IDBThroughputU" + this.ConnectorId).css("font-style", "italic").text("N/A");
		}
		
	}, generateEmptyCharts: function() {
			
		this.docCountChart = $.plot("#IDBDocCountChart" + this.ConnectorId, [{
			clickable: false, 
			hoverable: false, 
			label: "Docs found", 
			data: [[]]
		}, {
			clickable: false, 
			hoverable: false, 
			label: "Docs added to feed", 
			data: [[]]
		}], {
			legend: { show: true }, 
			xaxis: {
				show: true, 
				position: "bottom", 
				mode: "time",
				timeformat: "%Y/%m/%d %H:%M:%S", 
				timezone: "browser", 
				color: "rgb(113,112,115)", 
				tickColor: "rgba(113,112,115,0.2)"
			}, yaxis: {
				show: true, 
				position: "left", 
				color: "rgb(113,112,115)", 
				tickColor: "rgba(113,112,115,0.2)"
			}, grid: {
				show: true, 
				aboveData: false, 
				color: "rgba(113,112,115,0.2)", 
				backgroundColor: "rgba(113,112,115,0.02)"
			}, series: {
				lines: { show: true, fill: false, lineWidth: 1 },
				points: { show: false, fill: false }
			}
		});
		
		this.bytesCountChart = $.plot("#IDBBytesCountChart" + this.ConnectorId, [{
			clickable: false, 
			hoverable: false, 
			label: "Contents added to feed", 
			data: [[]]
		}, {
			clickable: false, 
			hoverable: false, 
			label: "Contents sent to GSA", 
			data: [[]]
		}], {
			legend: { show: true }, 
			xaxis: {
				show: true, 
				position: "bottom", 
				mode: "time",
				timeformat: "%Y/%m/%d %H:%M:%S", 
				timezone: "browser", 
				color: "rgb(113,112,115)", 
				tickColor: "rgba(113,112,115,0.2)"
			}, yaxis: {
				show: true, 
				position: "left", 
				color: "rgb(113,112,115)", 
				tickColor: "rgba(113,112,115,0.2)", 
				tickFormatter: this.formatSizeAxis
			}, grid: {
				show: true, 
				aboveData: false, 
				color: "rgba(113,112,115,0.2)", 
				backgroundColor: "rgba(113,112,115,0.02)"
			}, series: {
				lines: { show: true, fill: false, lineWidth: 1 },
				points: { show: false, fill: false }
			}
		});
	}, formatSizeAxis: function(val, axis){
		if (val > 1000) {
			return (val / 1000) + " MB";
		} else {
			return val + " kB";
		}
	}, getThreadInfoHTML: function(statsData){
		var Threads = "";
		var s = "";
		var t = "";
		for (var i in statsData.Threads) {
			t = statsData.Threads[i];
			var tClass = "Collapsed";
			if (($("#IDBThread" + this.ConnectorId + "_" + t.ID).length > 0) && $("#IDBThread" + this.ConnectorId + "_" + t.ID).hasClass("Expanded")) tClass = "Expanded";
			if ((i%2)==0) tClass += " Even";
			else tClass += " Odd"
			Threads += "<div id=\"IDBThread" + this.ConnectorId + "_" + t.ID + "\" class=\"IDBThread " + tClass + "\" ><div class=\"IDBTTitle\" >" + t.Name + " (#" + t.ID + ")</div><div class=\"IDBTStack\" >";
			if (t.Stack.length > 0) {
				for (var j in t.Stack) {
					s = t.Stack[j];
					Threads += "<div class=\"IDBTStackElem\" >" + s.Class + "#" + s.Method + "(" + s.File + ":" + s.ln + ")</div>";
				}
			} else {
				Threads += "<div class=\"IDBTStackElem\" >Empty stack trace</div>";
			}
			Threads += "</div></div>";
		}
		return Threads;
	}, ToggleStack: function() {
		var stackCont = $(this).parent();
		var stack = stackCont.find(".IDBTStack");
		if (stack.is(":visible")) {
			stackCont.removeClass("Expanded").addClass("Collapsed");
			stack.hide();
		} else {
			stackCont.removeClass("Collapsed").addClass("Expanded");
			stack.show();
		}
	}, ReloadStats: function(that){
		if (sword.DEBUG) console.log("Reloading stats for connector " + that.ConnectorId);
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/stats", 
			type: "GET", 
			data: { "id": that.ConnectorId }, 
			context: that, 
			error: that.StopUpdatingPage, 
			success: that.UpdatePageStats
		});
	}, UpdatePageStats: function(statsData) {
		this.updatePushInfoHTML(statsData);
		$("#IDBThreads" + this.ConnectorId).find(".IDBSectionContents").first().html(this.getThreadInfoHTML(statsData));
		this.HtmlElem.find(".IDBThread").not(".Expanded").find(".IDBTStack").hide();
	}, ReloadGraph: function(that){
		$.ajax({
			url: "/SCS/secure/restconf/connector/indexer/history", 
			type: "GET", 
			data: { "id": that.ConnectorId }, 
			context: that, 
			error: that.StopUpdatingPage, 
			success: that.UpdateGraph
		});
	}, UpdateGraph: function(data) {
		if (data.history.length > 0) {
		
			$("#IDBCharts" + this.ConnectorId).show();
			var allDocCountChartData = new Array();
			var allBytesCountChartData = new Array();
			var docsFoundData = null;
			var documentsAddedToFeedForIndexingData = null;
			var contentBytesAddedToFeedData = null;
			var bytesActuallySentToGsaData = null;
			var hist;
			var now;
			var lastKnownStart = -6;
			for (var i in data.history) {
				hist = data.history[i];
				if (hist.StartTime !== lastKnownStart) {
					lastKnownStart = hist.StartTime;
					var lblSuffix = (hist.IsRecovering ? " - recovery process started on " : " - process started on ") + new Date(hist.StartTime).toLocaleString();
					docsFoundData = { clickable: false, hoverable: false, label: "Doc. found" + lblSuffix, data: new Array() };
					allDocCountChartData.push(docsFoundData);
					documentsAddedToFeedForIndexingData = { clickable: false, hoverable: false, label: "Doc. added to feed" + lblSuffix, data: new Array() };
					allDocCountChartData.push(documentsAddedToFeedForIndexingData);
					contentBytesAddedToFeedData = { clickable: false, hoverable: false, label: "Contents added to feed" + lblSuffix, data: new Array() };
					allBytesCountChartData.push(contentBytesAddedToFeedData);
					bytesActuallySentToGsaData = { clickable: false, hoverable: false, label: "Contents sent to GSA" + lblSuffix, data: new Array() };
					allBytesCountChartData.push(bytesActuallySentToGsaData);
				}
				now = hist.Now;
				docsFoundData.data.push([now, hist.DocumentsFound]);
				documentsAddedToFeedForIndexingData.data.push([now, hist.DocumentsAddedToFeedForIndexing]);
				contentBytesAddedToFeedData.data.push([now, (hist.ContentBytesAddedToFeed / 1000)]);
				bytesActuallySentToGsaData.data.push([now, (hist.BytesActuallySentToGsa / 1000)]);
			}
			
			this.docCountChart.setData(allDocCountChartData);
			this.docCountChart.resize();
			this.docCountChart.setupGrid();
			this.docCountChart.draw();
			
			this.bytesCountChart.setData(allBytesCountChartData);
			this.bytesCountChart.resize();
			this.bytesCountChart.setupGrid();
			this.bytesCountChart.draw();

		} else {
			$("#IDBCharts" + this.ConnectorId).hide();
		}
	}, StopUpdatingPage: function(jqXHR, textStatus, errorThrown) {
		clearInterval(this.reloadStatsId);
		clearInterval(this.reloadGraphId);
		this.callback.call(this.callbackContext);
	}
};