import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import {ActivatedRoute, Params} from "@angular/router";
import { NgForm } from '@angular/forms';
import {Router} from "@angular/router";

@Component({
  selector: 'new-connector',
  templateUrl: './new.component.html',
  styleUrls: ['./new.component.css']
})
export class NewConnectorComponent implements OnInit {
  id: string;
  cid: string;
  authentication: string;
  groupretrieval: string;
  indexing: string;
  ns: string;
  connectorConf: ConnectorConf;
  connectorProps: ConnectorProps;
  waitingForCreate: boolean;
  constructor(private restApiService: RestApiService, private route: ActivatedRoute, private router: Router) {
    this.connectorConf = new ConnectorConf();
    this.waitingForCreate = false;
  }
  ngOnInit(): void {
    var self = this;
    this.route.params
      .switchMap(function(params: Params) {
        self.getConfig(params['classname']);
        return self.restApiService.getConnectorConfig(params['classname']);
      })
      .subscribe(function(connectorConf) {

        self.connectorConf = connectorConf;
      }
      );
  }
  onSubmit(form: NgForm) {
    var self = this;
    let formData = new FormData();
    formData.append("class", <any>this.connectorConf.className);
    for (var v of Object.keys(form.value)) {
      formData.append(v, form.value[v]);
    }
    this.restApiService.postConfig(formData).switchMap(function(result) {
      return new Promise(function(resolve) {
        self.waitingForCreate = true;
        setTimeout(resolve, 5000);
      });
    }
    ).subscribe(
      function(response) {
        self.waitingForCreate = false;
        self.router.navigateByUrl("/connectors/list");
      }
      );

  }

  getConfig(classname: string) {
    var self = this;
    this.restApiService.getConfig().subscribe(
      function(config) {
        self.connectorProps = <ConnectorProps>config.installedConnectors[classname];
        self.connectorProps.IsAuthN = self.connectorProps.IsAuthNDelegator || self.connectorProps.IsAuthNFormData || self.connectorProps.IsAuthNHeaders;
      }
    );

  }

}
export class ConnectorProps {
  IsAuthN: boolean;
  IsAuthNDelegator: boolean;
  IsAuthNFormData: boolean;
  IsAuthNHeaders: boolean;
  IsCachableGroupRetriever: boolean;
  IsGroupRetriever: boolean;
  IsIndexer: boolean;
  IsNameTransformer: boolean;
  IsAuthorizer: boolean;
}
export class ConnectorConf {
  className: string;
  confParams: any;
}
