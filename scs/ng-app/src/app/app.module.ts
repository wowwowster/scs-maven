import { RouterModule, Routes } from '@angular/router';
import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';

import {BrowserAnimationsModule} from '@angular/platform-browser/animations';

import {MdToolbarModule} from '@angular/material';
import {MdIconModule} from '@angular/material';
import {MdSidenavModule} from '@angular/material';
import {MdButtonModule} from '@angular/material';

import {MenuModule} from './menu/menu.module';
import {HomeModule} from './home/home.module';
import {ConnectorsModule} from './connectors/connectors.module';
import {RepositoriesModule} from './repositories/repositories.module';

import { AppComponent } from './app.component';

@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpModule,
    RouterModule,
    BrowserAnimationsModule,
    MdToolbarModule,
    MdIconModule,
    MdSidenavModule,
    MdButtonModule,
    MenuModule,
    HomeModule,
    ConnectorsModule,
    RepositoriesModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
