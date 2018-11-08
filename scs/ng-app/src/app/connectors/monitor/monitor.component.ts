import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import {ActivatedRoute, Params} from "@angular/router";
import { NgForm } from '@angular/forms';
import {Router} from "@angular/router";
import Chart from 'chart.js';

@Component({
  selector: 'monitor-connector',
  templateUrl: './monitor.component.html',
  styleUrls: ['./monitor.component.css']
})
export class MonitorConnectorComponent implements OnInit {
  id: string;
  connectorState: any;
  connectorStats: any;
  connectorConf: ConnectorConf;
  interval: any;
  constructor(private restApiService: RestApiService, private route: ActivatedRoute, private router: Router) {
    this.connectorConf = new ConnectorConf();
  }
  ngOnInit(): void {
    this.interval = this.startInterval(10, () => {
      var self = this;
      this.route.params
        .switchMap(function(params: Params) {
          self.id = params['id'];
          return self.restApiService.getConnectorConfigById(params['id']);
        })
        .subscribe(function(connectorConf) {
          self.connectorConf = connectorConf;
        }
        );
      this.route.params
        .switchMap(function(params: Params) {
          return self.restApiService.getConnectorStateById(params['id']);
        })
        .subscribe(function(connectorState) {
          self.connectorState = connectorState;
        }
        );
      this.route.params
        .switchMap(function(params: Params) {
          return self.restApiService.getConnectorLKStatsById(params['id']);
        })
        .subscribe(function(connectorStats) {
          self.connectorStats = connectorStats;
        }
        );
      this.route.params
        .switchMap(function(params: Params) {
          return self.restApiService.getConnectorLKHistoryById(params['id']);
        })
        .subscribe(function(history) {
          self.initChart(history);
        }
        );

    });
  }

  ngOnDestroy() {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }
  startConnector(): void {
    console.log("starting connector!");
    var self = this;
    this.restApiService.startConnector(self.id).switchMap(function(result) {
      return self.restApiService.getConnectorStateById(self.id);
    }).subscribe(function(connectorState) {
      self.connectorState = connectorState;
    });
  }

  initChart(history: any): void {
    var options = {
      animation: {
        duration: 0
      },
      responsive: true,
      legend: {
        display: true,
        labels: {
          fontColor: 'white'
        }
      },
      scales: {
        xAxes: [{
          type: "time",
          display: true,
          scaleLabel: {
            display: true,
            labelString: 'Date'
          }
        }],
        yAxes: [{
          display: true,
          scaleLabel: {
            display: true,
            labelString: 'Number of docs'
          }
        }]
      }

    };
    var options_tp = {
      responsive: true,
      legend: {
        display: true,
        labels: {
          fontColor: 'white'
        }
      },
      scales: {
        xAxes: [{
          type: "time",
          display: true,
          scaleLabel: {
            display: true,
            labelString: 'Date'
          }
        }],
        yAxes: [{
          display: true,
          scaleLabel: {
            display: true,
            labelString: 'Bytes sent'
          }
        }]
      }

    };
    var dataDocsFound = new Array();
    var dataDocsIndexed = new Array();
    var ContentBytesAddedToFeed = new Array();
    var BytesActuallySentToGsa = new Array();
    var i = 0;
    for (let point of Object.keys(history.history)) {
      //  data.push({ x: history.history[point].Now, y: history.history[point].DocumentsFound });
      dataDocsFound.push({ x: history.history[point].Now, y: 2 * i++ });
      dataDocsIndexed.push({ x: history.history[point].Now, y: i++ });
      ContentBytesAddedToFeed.push({ x: history.history[point].Now, y: 2 * i++ });
      BytesActuallySentToGsa.push({ x: history.history[point].Now, y: i++ });
    }
    var ctx = (<HTMLCanvasElement>document.getElementById("chart")).getContext('2d');
    var color = Chart.helpers.color;
    var myChart = new Chart(ctx, {
      type: 'line',
      data: {
        datasets: [{
          label: "Number of documents found",
          borderColor: "blue",
          backgroundColor: color("blue").alpha(0.5).rgbString(),
          fill: false,
          data: dataDocsFound
        },
          {
            label: "Number of documents added to feed",
            borderColor: "yellow",
            backgroundColor: color("yellow").alpha(0.5).rgbString(),
            fill: false,
            data: dataDocsIndexed
          }
        ]

      },
      options: options
    });
    var ctx_tp = (<HTMLCanvasElement>document.getElementById("chart_tp")).getContext('2d');
    var color = Chart.helpers.color;
    var myChart_tp = new Chart(ctx_tp, {
      type: 'line',
      data: {
        datasets: [{
          label: "Content Bytes added to feed",
          borderColor: "blue",
          backgroundColor: color("blue").alpha(0.5).rgbString(),
          fill: false,
          data: ContentBytesAddedToFeed
        },
          {
            label: "Bytes sent to Cloud Source",
            borderColor: "yellow",
            backgroundColor: color("yellow").alpha(0.5).rgbString(),
            fill: false,
            data: BytesActuallySentToGsa
          }
        ]

      },
      options: options
    });

  }

  startInterval(seconds, callback) {
    callback();
    return setInterval(callback, seconds * 1000);
  }

}
export class ConnectorConf {
  className: string;
  confParams: any;
  id: string;
}
