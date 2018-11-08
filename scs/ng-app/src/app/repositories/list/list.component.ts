import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import { NgForm } from '@angular/forms';

@Component({
  selector: 'list',
  templateUrl: './list.component.html',
  styleUrls: ['./list.component.css']
})
export class ListComponent implements OnInit {
  errorMessage: string;
  configured: number;
  installedRepositories: Array<any>;
  selectedOption: string;

  constructor(private restApiService: RestApiService) {

  }
  ngOnInit(): void {

    this.getConfig();
  }

  getConfig() {
    var self = this;
    this.restApiService.getConfig().subscribe(
      function(config) {
        self.configured = Object.keys(config.configuredCloudSources).length;
        self.installedRepositories = new Array();
        for (let key of Object.keys(config.configuredCloudSources)) {
          self.installedRepositories.push(config.configuredCloudSources[key]);
        }

      },
      error => this.errorMessage = <any>error
    );
  }
  openDialog() {

  }
  onSubmit(form: NgForm) {
      let formData = new FormData();
      for (var v of Object.keys(form.value)) {
        formData.append(v, form.value[v]);
      }
      this.restApiService.postSource(formData).subscribe(
        function(response) {
          console.log(response);
        }
      );

  }
}
