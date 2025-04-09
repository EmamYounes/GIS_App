package com.example.gisapp


import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.esri.arcgisruntime.concurrent.ListenableFuture
import com.esri.arcgisruntime.data.*
import com.esri.arcgisruntime.geometry.*
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.*
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.security.UserCredential
import com.esri.arcgisruntime.symbology.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class ParkingAreaMapActivity2 : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationButton: FloatingActionButton
    private var featureLayer: FeatureLayer? = null
    private val selectedFeatures = mutableListOf<Feature>()
    private val selectedOverlays = GraphicsOverlay()
    private var locationMarkerAdded = false
    private var hasZoomedToUserLocation = false

    private val parkingLayerUrl =
        "https://unifiedmap.shj.ae/server/rest/services/SHJMUN/Parking_AREAS/MapServer/0"

    private var preselectedAreaIDs: List<Int> = listOf(15, 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        locationButton = findViewById(R.id.locationButton)
        mapView.graphicsOverlays.add(selectedOverlays)

        setupMap()
        setupTapListener()
        setupLocationButton()
    }

    private fun setupLocationButton() {
        locationButton.setOnClickListener {
            val location = mapView.locationDisplay.location?.position
            if (location != null) {
                mapView.setViewpointAsync(Viewpoint(location, 5000.0))
                Toast.makeText(this, "Zoomed to current location", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMap() {
        val map = ArcGISMap(Basemap.createLightGrayCanvasVector())
        mapView.map = map

        val startPoint = Point(55.4033, 25.2744, SpatialReferences.getWgs84())
        mapView.setViewpoint(Viewpoint(startPoint, 10000.0))

        val serviceFeatureTable = ServiceFeatureTable(parkingLayerUrl).apply {
            credential = UserCredential("Sharjah_Digital_App", "ShjDigApp@123")
        }

        serviceFeatureTable.addDoneLoadingListener {
            if (serviceFeatureTable.loadStatus == LoadStatus.LOADED) {
                val layer = FeatureLayer(serviceFeatureTable)
                featureLayer = layer

                val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 2f)
                val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, 0x00000000, outlineSymbol)
                layer.renderer = SimpleRenderer(fillSymbol)

                // Disable default selection color and outline
                layer.selectionColor = Color.TRANSPARENT
                layer.selectionWidth = 0.0

                map.operationalLayers.add(layer)

                layer.addDoneLoadingListener {
                    if (layer.loadStatus == LoadStatus.LOADED) {
                        layer.fullExtent?.let {
                            mapView.setViewpointAsync(Viewpoint(it))
                        }
                    }
                }

                lifecycleScope.launch {
                    preselectFeatures(serviceFeatureTable)
                }
            } else {
                Log.e("Map", "FeatureTable failed to load: ${serviceFeatureTable.loadError?.message}")
            }
        }
        serviceFeatureTable.loadAsync()

        mapView.locationDisplay.apply {
            autoPanMode = LocationDisplay.AutoPanMode.RECENTER
            startAsync()
            addLocationChangedListener { event ->
                val location = event.location?.position ?: return@addLocationChangedListener
                val symbol = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.BLUE, 10f)

                if (!locationMarkerAdded) {
                    val graphic = Graphic(location, symbol)
                    val graphicsOverlay = GraphicsOverlay().apply {
                        graphics.add(graphic)
                    }
                    mapView.graphicsOverlays.add(graphicsOverlay)
                    mapView.tag = graphic
                    locationMarkerAdded = true
                } else {
                    val graphic = mapView.tag as? Graphic
                    graphic?.geometry = location
                }

                if (!hasZoomedToUserLocation) {
                    hasZoomedToUserLocation = true
//                    mapView.setViewpointAsync(Viewpoint(location, 5000.0))
                }
            }
        }
    }

    private fun preselectFeatures(featureTable: ServiceFeatureTable) {
        if (preselectedAreaIDs.isEmpty()) {
            Log.d("Map", "No preselectedAreaIDs provided")
            return
        }

        Log.d("Map", "PreselectedAreaIDs: $preselectedAreaIDs")
        val whereClause = "ID IN (${preselectedAreaIDs.joinToString(",")})"
        val query = QueryParameters().apply { this.whereClause = whereClause }

        val future: ListenableFuture<FeatureQueryResult>? = featureTable.queryFeaturesAsync(query)
        future?.addDoneListener {
            try {
                val result = future?.get()
                if (result != null) {
                    Log.d("Map", "Found ${result.count()} features to preselect")
                    for (feature in result) {
                        // Check if the feature ID is in the preselectedAreaIDs list
                        val featureId = feature.attributes["ID"].toString().toInt()
                        if (featureId != null && preselectedAreaIDs.contains(featureId)) {
//                            // Create a custom symbol for preselected features (different color)
//                            val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GRAY, 3f) // Red outline for preselected
//                            val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, 0x00000000, outlineSymbol)
//                            val renderer = SimpleRenderer(fillSymbol)
//
//                            // Apply the custom renderer to the specific feature
//                            featureLayer?.renderer = renderer

//                            selectedFeatures.add(feature)
                            featureLayer?.selectFeature(feature)
                            drawGrayOutlineOverlay(feature)

                        } else {
                            // Apply default renderer for non-preselected features
                            val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 2f) // Gray outline for non-preselected
                            val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, 0x00000000, outlineSymbol)
                            val renderer = SimpleRenderer(fillSymbol)

                            // Apply default renderer
                            featureLayer?.renderer = renderer
                        }

                        // Select and add to the list of selected features
                        featureLayer?.selectFeature(feature)
                        selectedFeatures.add(feature)
                    }
                }
                zoomToSelectedFeatures()
            } catch (e: Exception) {
                Log.e("Map", "Feature query error", e)
            }
        }
    }

    private fun setupTapListener() {
        mapView.setOnTouchListener(object : DefaultMapViewOnTouchListener(this, mapView) {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
                identifyFeatureManually(screenPoint)
                return true
            }
        })
    }

    private fun identifyFeatureManually(screenPoint: android.graphics.Point) {
        val layer = featureLayer ?: return

        val identifyFuture = mapView.identifyLayerAsync(layer, screenPoint, 10.0, false)
        identifyFuture.addDoneListener {
            try {
                val result = identifyFuture.get()
                val feature = result.elements.firstOrNull() as? Feature ?: return@addDoneListener
                val id = feature.attributes["ID"].toString().toIntOrNull()

                if (selectedFeatures.any { (it.attributes["ID"] as? Int) == id }) return@addDoneListener

                if (selectedFeatures.size >= 2) {
                    val first = selectedFeatures.removeAt(0)
                    featureLayer?.unselectFeature(first)
                    clearOverlayForFeature(first)
                }

                selectedFeatures.add(feature)
                featureLayer?.selectFeature(feature)
                drawGrayOutlineOverlay(feature)
                zoomToSelectedFeatures()

            } catch (e: Exception) {
                Log.e("Map", "Identify error", e)
            }
        }
    }

    private fun drawGrayOutlineOverlay(feature: Feature) {
        val geometry = feature.geometry ?: return

        val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GRAY, 3f)
        val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, 0x00000000, outlineSymbol)
        val graphic = Graphic(geometry, fillSymbol)

        selectedOverlays.graphics.add(graphic)
    }

    private fun clearOverlayForFeature(feature: Feature) {
        val geom = feature.geometry
        selectedOverlays.graphics.removeAll { it.geometry == geom }
    }

    private fun zoomToSelectedFeatures() {
        if (selectedFeatures.isEmpty()) return

        val geometries = selectedFeatures.mapNotNull { it.geometry }
        val union = GeometryEngine.union(geometries)
        val extent = union.extent ?: return

        val expanded = Envelope(extent.center, extent.width * 1.5, extent.height * 1.5)
        mapView.setViewpointAsync(Viewpoint(expanded))
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.dispose()
    }
}