import { Component, OnInit } from '@angular/core';
import {RestApiService} from '../../restapi/restapi.service'


@Component({
  selector: 'logs',
  templateUrl: './logs.component.html',
  styleUrls: ['./logs.component.css']
})
export class LogsComponent implements OnInit {
  errorMessage: string;
  logs: string;


  constructor(private restApiService: RestApiService) {

  }
  ngOnInit(): void {
    this.getLogs();

  }

  getLogs() {
    var self = this;
    this.restApiService.getLogs().subscribe(
      function(logs) {
        self.logs = logs.replace(new RegExp('\n', 'g'), "<br />");
      },
      error => self.errorMessage = <any>error
    );
  }
}
