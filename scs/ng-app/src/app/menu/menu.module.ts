import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';

import {BrowserAnimationsModule} from '@angular/platform-browser/animations';


import {MdIconModule} from '@angular/material';
import {MdListModule} from '@angular/material';

import {MenuComponent} from './menu.component';

import { AppRoutingModule} from '../app-routing.module';

@NgModule({
  declarations: [
    MenuComponent
  ],
  imports: [
    MdIconModule,
    MdListModule,
    AppRoutingModule

  ],
  exports: [MenuComponent],
  providers: [],
})
export class MenuModule { }
