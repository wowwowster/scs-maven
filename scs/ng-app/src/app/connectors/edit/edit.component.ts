import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import {ActivatedRoute, Params} from "@angular/router";
import { NgForm } from '@angular/forms';
import {Router} from "@angular/router";

@Component({
  selector: 'edit-connector',
  templateUrl: './edit.component.html',
  styleUrls: ['./edit.component.css']
})
export class EditConnectorComponent implements OnInit {
  cid: string;
  id: string;
  ns: string;
  indexing: boolean;
  connectorConf: ConnectorConf;
  connectorProps: ConnectorProps;
  constructor(private restApiService: RestApiService, private route: ActivatedRoute, private router: Router) {
    this.connectorConf = new ConnectorConf();
  }
  ngOnInit(): void {
    var self = this;
    this.route.params
      .switchMap(function(params: Params) {
        return self.restApiService.getConnectorConfigById(params['id']);
      })
      .subscribe(function(connectorConf) {
        self.getConfig(connectorConf.className);
        self.connectorConf = connectorConf;
        self.cid = connectorConf.id;
        self.indexing = connectorConf.IndexerConf != null
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

  onSubmit(form: NgForm) {
    console.log(form);
    var self = this;
    let formData = new FormData();
    formData.append("class", <any>this.connectorConf.className);
    formData.append("UseForPush", this.indexing ? "on" : "off");
    for (var v of Object.keys(form.value)) {
      formData.append(v, form.value[v]);
    }
    this.restApiService.postConfig(formData).subscribe(
      function(response) {
        self.router.navigateByUrl("/connectors/list");
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
