import { NgModule }              from '@angular/core';
import { RouterModule, Routes }  from '@angular/router';

import {ConnectorsComponent} from './connectors.component';
import {ListComponent} from './list/list.component';
import {NewConnectorComponent} from './new/new.component';
import {EditConnectorComponent} from './edit/edit.component';
import {MonitorConnectorComponent} from './monitor/monitor.component';
import {LogsConnectorComponent} from './logs/logs.component';
import {BrowseConnectorComponent} from './browse/browse.component'

const connectorsRoutes: Routes = [
  {
    path: 'connectors',
    component: ConnectorsComponent,
    children: [
      {
        path: 'list',
        component: ListComponent,
      },
      {
        path: 'new/:classname',
        component: NewConnectorComponent,
      },
      {
        path: 'edit/:id',
        component: EditConnectorComponent,
      },
      {
        path: 'monitor/:id',
        component: MonitorConnectorComponent,
      },
      {
        path: 'logs/:id',
        component: LogsConnectorComponent,
      },
      {
        path: 'browse/:id',
        component: BrowseConnectorComponent,
      },
      {
        path: '',
        redirectTo: "list", pathMatch: 'full'
      },
    ]
  }
];

@NgModule({
  imports: [
    RouterModule.forRoot(connectorsRoutes)
  ],
  exports: [
    RouterModule
  ]
})
export class ConnectorsRoutingModule { }
