import { NgModule }              from '@angular/core';
import { RouterModule, Routes }  from '@angular/router';

import {HomeComponent} from './home.component';
import {HomeListComponent} from './list/list.component'
import {LogsComponent} from './logs/logs.component';


const homeRoutes: Routes = [
  {
    path: 'home',
    component: HomeComponent,
    children: [
      { path: '', redirectTo: 'list', pathMatch: 'full' },
      {
        path: 'logs',
        component: LogsComponent,
      },
      {
        path: 'list',
        component: HomeListComponent,
      }

    ]
  }
];

@NgModule({
  imports: [
    RouterModule.forRoot(homeRoutes)
  ],
  exports: [
    RouterModule
  ]
})
export class HomeRoutingModule { }
