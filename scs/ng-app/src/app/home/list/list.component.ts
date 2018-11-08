import { Component, OnInit } from '@angular/core';
import {RestApiService, Misc, Config} from '../../restapi/restapi.service'

@Component({
  selector: 'list',
  templateUrl: './list.component.html',
  styleUrls: ['./list.component.css']
})
export class HomeListComponent implements OnInit {
  errorMessage: any;
  serverDate: string;
  uptime: string;
  installed: number;
  configured: number;
  cloudsources: number;

  constructor(private restApiService: RestApiService) {
  }
  ngOnInit(): void {

    this.getMisc();
    this.getConfig();
  }

  getMisc() {
    var self = this;
    this.restApiService.getMisc().subscribe(
      function(misc) {
        self.serverDate = misc.scs.ServerDate;
        self.uptime = misc.scs.Uptime;
      },
      error => this.errorMessage = <any>error
    );
  }
  getConfig() {
    var self = this;
    this.restApiService.getConfig().subscribe(
      function(config) {
        self.installed = Object.keys(config.installedConnectors).length;
        self.configured = Object.keys(config.configuredConnectors).length;
        self.cloudsources = Object.keys(config.configuredCloudSources).length;

      },
      error => this.errorMessage = <any>error
    );
  }
}
