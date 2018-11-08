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
import {MdTooltipModule} from '@angular/material';
import {MdDialogModule} from '@angular/material';
import {MdSelectModule} from '@angular/material';
import {MdInputModule} from '@angular/material';
import {MdCheckboxModule} from '@angular/material';
import {MdSlideToggleModule} from '@angular/material';
import {MdGridListModule} from '@angular/material';
import {MdProgressBarModule} from '@angular/material';

import { ConnectorsRoutingModule} from './connectors-routing.module';


import {ConnectorsComponent} from './connectors.component';
import {ListComponent} from './list/list.component';
import {AddConnectorDialog} from './list/list.component';
import {NewConnectorComponent} from './new/new.component';
import {EditConnectorComponent} from './edit/edit.component';
import {MonitorConnectorComponent} from './monitor/monitor.component';
import {LogsConnectorComponent} from './logs/logs.component';
import {BrowseConnectorComponent} from './browse/browse.component';


@NgModule({
  declarations: [
    ConnectorsComponent,
    ListComponent,
    AddConnectorDialog,
    NewConnectorComponent,
    EditConnectorComponent,
    MonitorConnectorComponent,
    LogsConnectorComponent,
    BrowseConnectorComponent

  ],
  imports: [
    CommonModule,
    MdIconModule,
    MdListModule,
    MdButtonModule,
    MdCardModule,
    ConnectorsRoutingModule,
    MdTooltipModule,
    MdDialogModule,
    MdSelectModule,
    FormsModule,
    MdInputModule,
    MdCheckboxModule,
    MdSlideToggleModule,
    MdGridListModule,
    MdProgressBarModule
  ],
  entryComponents: [AddConnectorDialog],
  exports: [ConnectorsComponent, ListComponent],
  providers: [RestApiService],
})
export class ConnectorsModule { }
