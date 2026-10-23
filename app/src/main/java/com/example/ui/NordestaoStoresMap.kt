package com.example.ui

import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NordestaoStore
import org.json.JSONArray
import org.json.JSONObject

internal fun storesMapHtml(stores: List<NordestaoStore>): String {
    val data = JSONArray(stores.filter { it.latitude != null && it.longitude != null }.map {
        JSONObject().put("id",it.id).put("name",it.name).put("address",it.address).put("lat",it.latitude).put("lon",it.longitude)
    }).toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    return """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src https://unpkg.com 'unsafe-inline'; style-src https://unpkg.com 'unsafe-inline'; img-src https://tile.openstreetmap.org https://unpkg.com data:; connect-src 'none'; base-uri 'none'; form-action 'none'">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="">
<style>html,body,#map{height:100%;margin:0}body{font-family:sans-serif}.leaflet-popup-content{white-space:pre-line}#error{padding:20px}</style></head><body><div id="map"></div><div id="error" hidden>Não foi possível carregar o mapa. Volte para Lista para consultar as lojas.</div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
<script>if(typeof L==='undefined'){document.getElementById('map').hidden=true;document.getElementById('error').hidden=false;}else{
const stores=$data;const map=L.map('map').setView([-5.8,-35.3],10);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
const bounds=[];for(const s of stores){const box=document.createElement('div');const title=document.createElement('strong');title.textContent=s.name;box.appendChild(title);const addr=document.createElement('p');addr.textContent=s.address;box.appendChild(addr);const a=document.createElement('a');a.href='nrd-store://'+s.id;a.textContent='Ver detalhes da loja';box.appendChild(a);L.marker([s.lat,s.lon]).addTo(map).bindPopup(box);bounds.push([s.lat,s.lon]);}if(bounds.length)map.fitBounds(bounds,{padding:[25,25],maxZoom:15});}
</script></body></html>"""
}
@Composable
internal fun NordestaoStoresMap(stores: List<NordestaoStore>, onSelect: (Long) -> Unit, onExternal: (Uri) -> Unit) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val external by rememberUpdatedState(onExternal)
    val currentStores by rememberUpdatedState(stores)
    val webView = remember(context) { WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false; settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString += " NRDLOJAS/Stores (+https://github.com/bichocutela/NRDLOJAS-v2)"
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                if(uri.scheme == "nrd-store") uri.host?.toLongOrNull()?.takeIf { id -> currentStores.any { it.id == id } }?.let { select(it) }
                else if(uri.scheme == "https" && uri.host == "www.openstreetmap.org") external(uri)
                return true
            }
        }
    } }
    val html = remember(stores) { storesMapHtml(stores) }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize(), update = {
        if(it.tag != html) { it.tag = html; it.loadDataWithBaseURL("https://appassets.androidplatform.net/nrd-stores/",html,"text/html","UTF-8",null) }
    })
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
}
