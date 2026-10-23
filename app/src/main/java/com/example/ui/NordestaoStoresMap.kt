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

internal fun storesMapHtml(stores: List<NordestaoStore>, leafletJs: String, leafletCss: String): String {
    val data = JSONArray(stores.filter { it.latitude != null && it.longitude != null }.map {
        JSONObject().put("id",it.id).put("name",it.name).put("address",it.address).put("lat",it.latitude).put("lon",it.longitude)
    }).toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    return """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src https://tile.openstreetmap.org data:; connect-src 'none'; base-uri 'none'; form-action 'none'">
<style>$leafletCss
html,body,#map{height:100%;width:100%;margin:0}#map{position:absolute;inset:0}body{font-family:sans-serif}.leaflet-popup-content{white-space:pre-line}.store-pin{background:#d93826;border:3px solid white;border-radius:50%;box-shadow:0 1px 5px #333}#error{padding:20px;position:relative;background:white}</style></head><body><div id="map"></div><div id="error" hidden>Não foi possível carregar o mapa. Volte para Lista para consultar as lojas.</div>
<script>$leafletJs</script>
<script>if(typeof L==='undefined'){document.getElementById('map').hidden=true;document.getElementById('error').hidden=false;}else{
const stores=$data;const map=L.map('map').setView([-5.8,-35.3],10);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
const bounds=[];for(const s of stores){const box=document.createElement('div');const title=document.createElement('strong');title.textContent=s.name;box.appendChild(title);const addr=document.createElement('p');addr.textContent=s.address;box.appendChild(addr);const a=document.createElement('a');a.href='nrd-store://'+s.id;a.textContent='Ver detalhes da loja';box.appendChild(a);L.marker([s.lat,s.lon],{icon:L.divIcon({className:'store-pin',iconSize:[18,18],iconAnchor:[9,9]})}).addTo(map).bindPopup(box);bounds.push([s.lat,s.lon]);}if(bounds.length)map.fitBounds(bounds,{padding:[25,25],maxZoom:15});new ResizeObserver(()=>map.invalidateSize()).observe(document.getElementById('map'));}
</script></body></html>"""
}
@Composable
internal fun NordestaoStoresMap(stores: List<NordestaoStore>, onSelect: (Long) -> Unit, onExternal: (Uri) -> Unit) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val external by rememberUpdatedState(onExternal)
    val currentStores by rememberUpdatedState(stores)
    val webView = remember(context) { WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
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
    val assets = remember(context) {
        listOf("leaflet.js", "leaflet.css").map { name -> context.assets.open("store-map/$name").bufferedReader().use { it.readText() } }
    }
    val html = remember(stores, assets) { storesMapHtml(stores, assets[0], assets[1]) }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize(), update = {
        if(it.tag != html) { it.tag = html; it.loadDataWithBaseURL("https://appassets.androidplatform.net/nrd-stores/",html,"text/html","UTF-8",null) }
    })
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
}
