import { Component, OnInit, Inject } from '@angular/core';
import {RestApiService, Config} from '../../restapi/restapi.service'
import {ActivatedRoute, Params} from "@angular/router";
import { NgForm } from '@angular/forms';
import {Router} from "@angular/router";
import Chart from 'chart.js';

@Component({
  selector: 'browse-connector',
  templateUrl: './browse.component.html',
  styleUrls: ['./browse.component.css']
})
export class BrowseConnectorComponent implements OnInit {
  id: string;

  constructor(private restApiService: RestApiService, private route: ActivatedRoute, private router: Router) {

  }
  ngOnInit(): void {

    var self = this;
    this.route.params
      .switchMap(function(params: Params) {
        self.id = params['id'];
        return self.restApiService.startDbBrowsing(params['id']);
      })
      .switchMap(function(results) {
        console.log(results);
        return self.restApiService.getConnectorDbState(self.id);
      })
      .subscribe(function(results) {
        console.log(results);
      });

  }




}
