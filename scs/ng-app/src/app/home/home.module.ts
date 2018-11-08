import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';
import { CommonModule } from '@angular/common';

import {BrowserAnimationsModule} from '@angular/platform-browser/animations';

import {RestApiService} from '../restapi/restapi.service'


import {MdIconModule} from '@angular/material';
import {MdListModule} from '@angular/material';
import {MdCardModule} from '@angular/material';
import {MdButtonModule} from '@angular/material';
import {MdChipsModule} from '@angular/material';
import {MdProgressBarModule} from '@angular/material';
import {MdProgressSpinnerModule} from '@angular/material';


import {HomeComponent} from './home.component';
import {HomeListComponent} from './list/list.component';
import {LogsComponent} from './logs/logs.component';
import {HomeRoutingModule} from './home-routing.module';

@NgModule({
  declarations: [
    HomeComponent,
    HomeListComponent,
    LogsComponent
  ],
  imports: [
    CommonModule,
    MdIconModule,
    MdListModule,
    MdButtonModule,
    MdCardModule,
    MdChipsModule,
    HomeRoutingModule,
    MdProgressBarModule,
    MdProgressSpinnerModule
  ],
  exports: [HomeComponent, HomeListComponent, LogsComponent],
  providers: [RestApiService],
})
export class HomeModule { }
