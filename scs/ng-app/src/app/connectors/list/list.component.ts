import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import {MdDialog, MdDialogRef} from '@angular/material';
import {MD_DIALOG_DATA} from '@angular/material';
import {Router} from "@angular/router";

@Component({
  selector: 'list',
  templateUrl: './list.component.html',
  styleUrls: ['./list.component.css']
})
export class ListComponent implements OnInit {
  errorMessage: string;
  configured: number;
  installedConnectors: Array<any>;
  configuredConnectors: Array<any>;
  waitingForDelete: boolean

  constructor(private restApiService: RestApiService, public dialog: MdDialog, private router: Router) {
    this.waitingForDelete = false;
  }
  ngOnInit(): void {

    this.getConfig();
  }

  getConfig() {
    var self = this;
    this.restApiService.getConfig().subscribe(
      function(config: Config) {
        self.configured = Object.keys(config.configuredConnectors).length;
        self.installedConnectors = new Array();
        self.configuredConnectors = new Array();
        for (let key of Object.keys(config.installedConnectors)) {
          self.installedConnectors.push(config.installedConnectors[key]);
        }
        for (let key of Object.keys(config.configuredConnectors)) {
          self.configuredConnectors.push({ name: key, infos: config.installedConnectors[config.configuredConnectors[key]] });
        }
        console.log(self.configuredConnectors);

      },
      error => this.errorMessage = <any>error
    );
  }

  deleteConnector(id: string) {
    var self = this;
    this.restApiService.deleteConnector(id).switchMap(function(result) {
      return new Promise(function(resolve) {
        self.waitingForDelete = true;
        setTimeout(resolve, 5000);
      });
    }
    ).switchMap(function(result) {
      return self.restApiService.getConfig();
    }
      ).subscribe(function(config: Config) {
        self.configured = Object.keys(config.configuredConnectors).length;
        self.installedConnectors = new Array();
        self.configuredConnectors = new Array();
        for (let key of Object.keys(config.installedConnectors)) {
          self.installedConnectors.push(config.installedConnectors[key]);
        }
        for (let key of Object.keys(config.configuredConnectors)) {
          self.configuredConnectors.push({ name: key, infos: config.installedConnectors[config.configuredConnectors[key]] });
        }
        self.waitingForDelete = false;

      },
      error => this.errorMessage = <any>error);
  }

  openDialog() {
    var self = this;
    let dialogRef = this.dialog.open(AddConnectorDialog, { height: '300px', width: '400px', data: this.installedConnectors });
    var connectorDialog = dialogRef.componentInstance;
    dialogRef.afterClosed().subscribe(result => {
      if (result == "OK") {
        console.log(connectorDialog.myConnector);
        if (connectorDialog.myConnector == null) {
          dialogRef = self.dialog.open(AddConnectorDialog, { height: '300px', width: '400px', data: self.installedConnectors });
          dialogRef.componentInstance.error = true;
        }
        else {
          connectorDialog.error = false;
          self.router.navigateByUrl("/connectors/new/" + connectorDialog.myConnector);

        }
      }
      else {
        console.log("cancel");
      }
    });

  }
}
@Component({
  selector: 'add-connector-dialog',
  templateUrl: './add-connector-dialog.html',
  styleUrls: ['./add-connector-dialog.css']
})
export class AddConnectorDialog {
  myConnector: string;
  error: boolean;
  constructor(public dialogRef: MdDialogRef<AddConnectorDialog>, @Inject(MD_DIALOG_DATA) public data: any) { }
}
