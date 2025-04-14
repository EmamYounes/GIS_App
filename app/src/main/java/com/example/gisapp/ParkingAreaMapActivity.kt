package com.example.gisapp

import android.annotation.SuppressLint
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
import androidx.core.graphics.toColorInt

/**
 * Activity that displays a map showing parking areas using ArcGIS SDK.
 * Supports preselecting areas, displaying user location, and feature selection on tap.
 */
class ParkingAreaMapActivity : AppCompatActivity() {

    // MapView used to display the ArcGIS map
    private lateinit var mapView: MapView

    // Floating action button to zoom to user location
    private lateinit var locationButton: FloatingActionButton

    // Feature layer for parking areas
    private var featureLayer: FeatureLayer? = null

    // List of currently selected features
    private val selectedFeatures = mutableListOf<Feature>()

    // Overlay used to draw custom graphics on top of selected features
    private val selectedOverlays = GraphicsOverlay()

    // Flags to ensure location marker and zoom are applied only once
    private var locationMarkerAdded = false
    private var hasZoomedToUserLocation = false

    // URL of the parking feature layer
    private val parkingLayerUrl = "https://unifiedmap.shj.ae/server/rest/services/SHJMUN/Parking_AREAS/MapServer/0"

    // Area IDs to be preselected when the map is loaded
    private var preselectedAreaIDs: List<Int> = listOf(15, 16)

    /**
     * Initializes UI and map when activity is created.
     */
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

    /**
     * Sets up listener for location button to zoom to user's current location.
     */
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

    /**
     * Initializes the map, loads the parking feature layer,
     * sets up symbol renderer, location tracking, and preselects specific features.
     */
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

                // Default symbol renderer for unselected features
                val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 2f)
                val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, 0x00000000, outlineSymbol)
                layer.renderer = SimpleRenderer(fillSymbol)

                // Remove default selection styling
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

    /**
     * Queries and selects features from the feature table based on preselectedAreaIDs.
     */
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
                val result = future.get()
                if (result != null) {
                    Log.d("Map", "Found ${result.count()} features to preselect")
                    for (feature in result) {
                        val featureId = feature.attributes["ID"].toString().toInt()
                        if (preselectedAreaIDs.contains(featureId)) {
                            featureLayer?.selectFeature(feature)
                            drawGrayOutlineOverlay(feature)
                        } else {
                            val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 2f)
                            val fillSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, 0x00000000, outlineSymbol)
                            val renderer = SimpleRenderer(fillSymbol)
                            featureLayer?.renderer = renderer
                        }

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

    /**
     * Sets up listener to detect user taps on the map and identify tapped features.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapListener() {
        mapView.setOnTouchListener(object : DefaultMapViewOnTouchListener(this, mapView) {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
                identifyFeatureManually(screenPoint)
                return true
            }
        })
    }

    /**
     * Identifies features at the tapped location and selects them.
     * Limits selection to 2 features at a time.
     */
    private fun identifyFeatureManually(screenPoint: android.graphics.Point) {
        val layer = featureLayer ?: return

        val identifyFuture = mapView.identifyLayerAsync(layer, screenPoint, 10.0, false)
        identifyFuture.addDoneListener {
            try {
                val result = identifyFuture.get()
                val feature = result.elements.firstOrNull() as? Feature ?: return@addDoneListener
                val clickedId = feature.attributes["ID"]?.toString()?.toIntOrNull()

                val alreadySelected = selectedFeatures.any {
                    it.attributes["ID"]?.toString()?.toIntOrNull() == clickedId
                }
                if (alreadySelected) return@addDoneListener

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

    /**
     * Draws a filled light green polygon with a gray outline to represent a selected feature.
     */
    private fun drawGrayOutlineOverlay(feature: Feature) {
        val geometry = feature.geometry ?: return

        // Draw filled polygon with gray outline
        val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GRAY, 2f)
        val fillSymbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID,
            "#A5D6A7".toColorInt(),
            outlineSymbol
        )
        val polygonGraphic = Graphic(geometry, fillSymbol)
        selectedOverlays.graphics.add(polygonGraphic)

        // Extract center point of the polygon
        val centerPoint = geometry.extent?.center ?: return

        // Get area name from attributes
        val areaName = feature.attributes["NAME_ENGLISH"]?.toString() ?: ""

        // Create a text symbol
        val textSymbol = TextSymbol(
            14f,
            areaName,
            Color.BLACK,
            TextSymbol.HorizontalAlignment.CENTER,
            TextSymbol.VerticalAlignment.MIDDLE
        ).apply {
            outlineColor = Color.WHITE
            outlineWidth = 2f
        }

        // Add text label as graphic
        val textGraphic = Graphic(centerPoint, textSymbol)
        selectedOverlays.graphics.add(textGraphic)
    }


    /**
     * Clears graphic overlays for a given feature.
     */
    private fun clearOverlayForFeature(feature: Feature) {
        val geom = feature.geometry
        selectedOverlays.graphics.removeAll { it.geometry == geom }
    }

    /**
     * Zooms the map to encompass all currently selected features.
     */
    private fun zoomToSelectedFeatures() {
        if (selectedFeatures.isEmpty()) return

        val geometries = selectedFeatures.mapNotNull { it.geometry }
        val union = GeometryEngine.union(geometries)
        val extent = union.extent ?: return

        val expanded = Envelope(extent.center, extent.width * 1.5, extent.height * 1.5)
        mapView.setViewpointAsync(Viewpoint(expanded))
    }

    /**
     * Pauses the map view to save resources.
     */
    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    /**
     * Resumes the map view when activity is resumed.
     */
    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    /**
     * Cleans up the map view to prevent memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        mapView.dispose()
    }
}
