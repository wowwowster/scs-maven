import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import {ActivatedRoute, Params} from "@angular/router";
import { NgForm } from '@angular/forms';
import {Router} from "@angular/router";
import Chart from 'chart.js';

@Component({
  selector: 'logs-connector',
  templateUrl: './logs.component.html',
  styleUrls: ['./logs.component.css']
})
export class LogsConnectorComponent implements OnInit {
  id: string;
  logs: string;
  interval: any;

  constructor(private restApiService: RestApiService, private route: ActivatedRoute, private router: Router) {

  }
  ngOnInit(): void {
    this.interval = this.startInterval(10, () => {
      var self = this;
      this.route.params
        .switchMap(function(params: Params) {
          self.id = params['id'];
          return self.restApiService.getConnectorLogsById(params['id']);
        })
        .subscribe(function(logs) {
          self.logs = logs.replace(new RegExp('\n', 'g'), "<br />");
        });
    });

  }

  ngOnDestroy() {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }


  startInterval(seconds, callback) {
    callback();
    return setInterval(callback, seconds * 1000);
  }

}
