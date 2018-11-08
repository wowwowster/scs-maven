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

import { RepositoriesRoutingModule} from './repositories-routing.module';


import {RepositoriesComponent} from './repositories.component';
import {ListComponent} from './list/list.component';

@NgModule({
  declarations: [
    RepositoriesComponent,
    ListComponent
  ],
  imports: [
    CommonModule,
    MdIconModule,
    MdListModule,
    MdButtonModule,
    MdCardModule,
    RepositoriesRoutingModule,
    MdTooltipModule,
    MdDialogModule,
    MdSelectModule,
    FormsModule
  ],
  exports: [RepositoriesComponent, ListComponent],
  providers: [RestApiService],
})
export class RepositoriesModule { }
