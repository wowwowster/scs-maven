import { Injectable }              from '@angular/core';
import { Http, Response, Headers }          from '@angular/http';
import { Observable } from 'rxjs/Observable';
import 'rxjs/add/operator/catch';
import 'rxjs/add/operator/map';

@Injectable()
export class RestApiService {
  private miscUrl = 'https://localhost:8443' + '/SCS/secure/restconf/scs/misc';  // URL to web API
  private configUrl = 'https://localhost:8443' + '/SCS/secure/restconf/scs/config';
  private logsUrl = 'https://localhost:8443' + '/SCS/secure/restconf/scs/logs';
  private connectorPostConfigUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/config';
  private SourcePostUrl = 'https://localhost:8443' + '/SCS/secure/restconf/scs/sb';
  private connectorConfigUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/config?class=';
  private connectorConfigIdUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/config?id=';
  private connectorStateUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/state?id=';
  private connectorLKStatsUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/LastKnownStats?id=';
  private connectorLKHistoryUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/LastKnownHistory?id=';
  private connectorStartUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/start?id=';
  private connectorLogsUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/log?id=';
  private startDbBrowsingUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/startdbbrowsing';
  private stopDbBrowsingUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/stopdbbrowsing';
  private stateDbBrowsingUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/state?id=';
  private childNodeDbBrowsingUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/indexer/internaldb?';
  private deleteConnectorUrl = 'https://localhost:8443' + '/SCS/secure/restconf/connector/delete';
  constructor(private http: Http) { }
  getMisc(): Observable<Misc> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.miscUrl, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  startDbBrowsing(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    let formData = new FormData();
    formData.append("id", id);
    return this.http.post(this.startDbBrowsingUrl, formData, { headers: headers })
      .catch(this.handleError);
  }
  stopDbBrowsing(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    let formData = new FormData();
    formData.append("id", id);
    return this.http.post(this.stopDbBrowsingUrl, formData, { headers: headers })
      .catch(this.handleError);
  }
  getConnectorDbState(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.stateDbBrowsingUrl + id, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  getConnectorDbChildNodes(id: string, num: string, page: string, parent: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    let queryParams = `id=${id}&num=${num}&page=${page}&parent=${parent}`;
    return this.http.get(this.childNodeDbBrowsingUrl + queryParams, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  startConnector(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.post(this.connectorStartUrl + id, null, { headers: headers })
      .catch(this.handleError);
  }
  getConnectorConfig(classname: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.connectorConfigUrl + classname, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  getConnectorLogsById(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.connectorLogsUrl + id, { headers: headers })
      .map(function(res) { return res.text(); })
      .catch(this.handleError);
  }
  getConnectorStateById(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.connectorStateUrl + id, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  getConnectorLKStatsById(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.connectorLKStatsUrl + id, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  getConnectorLKHistoryById(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.connectorLKHistoryUrl + id, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  getConnectorConfigById(id: string): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.connectorConfigIdUrl + id, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  getConfig(): Observable<Config> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.configUrl, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  postConfig(formData: FormData): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.post(this.connectorPostConfigUrl, formData, { headers: headers })
      .catch(this.handleError);
  }
  postSource(formData: FormData): Observable<any> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.post(this.SourcePostUrl, formData, { headers: headers })
      .map(this.extractData)
      .catch(this.handleError);
  }
  deleteConnector(id: string): Observable<any> {
    let headers = new Headers();
    let formData = new FormData();
    formData.append("id", id);
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.post(this.deleteConnectorUrl, formData, { headers: headers })
      .catch(this.handleError);
  }
  getLogs(): Observable<string> {
    let headers = new Headers();
    headers.append('Authorization', 'Basic ' + btoa('SCSAdmin:sword75'));
    return this.http.get(this.logsUrl, { headers: headers })
      .map(function(res) { return res.text(); })
      .catch(this.handleError);
  }
  private extractData(res: Response) {
    let body = res.json();
    console.log(body);
    return body || {};
  }
  private handleError(error: Response | any) {
    // In a real world app, you might use a remote logging infrastructure
    let errMsg: string;
    if (error instanceof Response) {
      const body = error.json() || '';
      const err = body.error || JSON.stringify(body);
      errMsg = `${error.status} - ${error.statusText || ''} ${err}`;
    } else {
      errMsg = error.message ? error.message : error.toString();
    }
    console.error(errMsg);
    return Observable.throw(errMsg);
  }
}

export class Misc {
  scs: any;
  gsa: any;
}

export class Config {
  CurrentUser: any;
  authnConf: any;
  authzConf: any;
  configuredCloudSources: any;
  configuredConnectors: any;
  gsa_info: any;
  indexers: any;
  installedConnectors: any;
}
