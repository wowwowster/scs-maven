import { NgModule }              from '@angular/core';
import { RouterModule, Routes }  from '@angular/router';

import {RepositoriesComponent} from './repositories.component';
import {ListComponent} from './list/list.component';


const repositoriesRoutes: Routes = [
  {
    path: 'repositories',
    component: RepositoriesComponent,
    children: [
      {
        path: 'list',
        component: ListComponent,
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
    RouterModule.forRoot(repositoriesRoutes)
  ],
  exports: [
    RouterModule
  ]
})
export class RepositoriesRoutingModule { }
