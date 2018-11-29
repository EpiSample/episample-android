package org.taskforce.episample.collection.ui

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.Toast
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapboxMapOptions
import com.mapbox.mapboxsdk.maps.SupportMapFragment
import kotlinx.android.synthetic.main.fragment_collect.*
import org.taskforce.episample.BuildConfig
import org.taskforce.episample.EpiApplication
import org.taskforce.episample.R
import org.taskforce.episample.collection.managers.CollectIconFactory
import org.taskforce.episample.collection.managers.MapboxItemMarkerManager
import org.taskforce.episample.collection.viewmodels.CollectCardViewModel
import org.taskforce.episample.collection.viewmodels.CollectViewModel
import org.taskforce.episample.collection.viewmodels.CollectViewModelFactory
import org.taskforce.episample.core.LiveDataPair
import org.taskforce.episample.core.interfaces.CollectItem
import org.taskforce.episample.core.interfaces.Config
import org.taskforce.episample.databinding.FragmentCollectBinding
import org.taskforce.episample.help.HelpUtil
import org.taskforce.episample.mapbox.MapboxLayersFragment
import org.taskforce.episample.navigation.ui.NavigationToolbarViewModel
import org.taskforce.episample.navigation.ui.NavigationToolbarViewModelFactory
import org.taskforce.episample.utils.getCompatColor
import org.taskforce.episample.utils.inflater
import org.taskforce.episample.utils.toMapboxLatLng
import javax.inject.Inject

class CollectFragment : Fragment(), MapboxMap.OnMarkerClickListener, MapboxMap.OnMapClickListener {

    @Inject
    lateinit var config: Config

    lateinit var collectIconFactory: CollectIconFactory

    lateinit var navigationToolbarViewModel: NavigationToolbarViewModel
    lateinit var collectViewModel: CollectViewModel

    var adapter: CollectItemAdapter? = null
    lateinit var breadcrumbPath: PolylineOptions
    lateinit var collectCardVm: CollectCardViewModel

    private var mapFragment: SupportMapFragment? = null
    private val markerManagerLiveData = MutableLiveData<MapboxItemMarkerManager>()

    val mapPreferences: SharedPreferences
        get() = requireActivity().getSharedPreferences(MAP_PREFERENCE_NAMESPACE, Context.MODE_PRIVATE)

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as EpiApplication).collectComponent?.inject(this)

        Mapbox.getInstance(requireContext(), BuildConfig.MAPBOX_ACCESS_TOKEN)

        collectIconFactory = CollectIconFactory(requireContext().resources)

        breadcrumbPath = PolylineOptions()

        collectViewModel = ViewModelProviders.of(this@CollectFragment,
                CollectViewModelFactory(
                        requireActivity().application,
                        {
                            showCollectAddScreen(it)
                        },
                        {
                            requireActivity().supportFragmentManager.popBackStack()
                        })).get(CollectViewModel::class.java)

        navigationToolbarViewModel = ViewModelProviders.of(this,
                NavigationToolbarViewModelFactory(
                        requireActivity().application,
                        R.string.toolbar_collect_title
                )).get(NavigationToolbarViewModel::class.java)

        val userSettings = collectViewModel.config.userSettings
        val enumerationSubject = collectViewModel.config.enumerationSubject
        val displaySettings = collectViewModel.config.displaySettings

        collectCardVm = CollectCardViewModel(userSettings,
                enumerationSubject,
                displaySettings,
                requireContext().getCompatColor(R.color.colorError),
                requireContext().getCompatColor(R.color.colorWarning),
                requireContext().getCompatColor(R.color.gpsAcceptable))

        lifecycle.addObserver(collectViewModel.locationService)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentCollectBinding.inflate(inflater.context.inflater).apply {
            vm = collectViewModel
            cardVm = collectCardVm
            toolbarVm = navigationToolbarViewModel
        }

        setHasOptionsMenu(true)
        val toolbar = binding.root.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        adapter = CollectItemAdapter(CollectIconFactory(requireContext().resources),
                getString(R.string.collect_incomplete),
                collectViewModel.config.displaySettings
        ) {
            showItemDetailsScreen(it)
        }
        binding.collectList.adapter = adapter

        LiveDataPair(markerManagerLiveData, collectViewModel.collectItems).observe(this, Observer {
            it?.let { (markerManager, items) ->

                val sortedItems = items.sortedByDescending { it.dateCreated }
                adapter?.data = sortedItems

                val titleText = String.format(getString(R.string.collect_title_var), "${items.size}")
                collectTitle.text = titleText

                markerManager.addMarkerDiff(items)
            }
        })

        binding.setLifecycleOwner(this)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            mapFragment = SupportMapFragment.newInstance(
                    MapboxMapOptions()
                            .styleUrl(collectViewModel.config.mapboxStyle.urlString)
            )
            childFragmentManager
                    .beginTransaction()
                    .replace(R.id.collectionMap, mapFragment, MAP_FRAGMENT_TAG)
                    .commit()

        } else {
            mapFragment = childFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG) as SupportMapFragment
        }

        mapFragment?.getMapAsync {
            markerManagerLiveData.postValue(MapboxItemMarkerManager(requireContext(), mapPreferences, it))

            it.setOnMarkerClickListener(this@CollectFragment)
            it.addOnMapClickListener(this@CollectFragment)
        }
        
        markerManagerLiveData.observe(this, Observer { markerManager -> 
            markerManager?.let {
                it.addEnumerationAreas(config.enumerationAreas)
            }
        })

        LiveDataPair(markerManagerLiveData, collectViewModel.locationService.locationLiveData).observe(this, Observer {
            it?.let { (markerManager, locationUpdate) ->
                locationUpdate.let { (latLng, accuracy) ->
                    collectCardVm.currentLocation.set(latLng)
                    markerManager.setCurrentLocation(latLng.toMapboxLatLng(), accuracy.toDouble())

                    if (collectViewModel.lastKnownLocation == null) {
                        mapFragment?.getMapAsync { map ->
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng.toMapboxLatLng(), CollectViewModel.zoomLevel))
                        }
                    }

                    collectViewModel.lastKnownLocation = latLng.toMapboxLatLng()
                }
            }
        })

        mapFragment?.onCreate(savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)

        toolbar?.setNavigationOnClickListener {
            if (fragmentManager?.backStackEntryCount ?: 0 > 0) {
                fragmentManager?.popBackStack()
            } else {
                requireActivity().finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        collectViewModel.lastKnownLocation?.let { lastLocation ->
            mapFragment?.getMapAsync {
                it.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLocation, CollectViewModel.zoomLevel))
            }
        }

        markerManagerLiveData.observe(this, Observer {
            it?.let {
                it.applyLayerSettings()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.map, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.center_my_location -> {
                mapFragment?.getMapAsync {
                    markerManagerLiveData.value?.getCurrentLocation()?.let { currentLocation ->
                        it.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, CollectViewModel.zoomLevel))
                    } ?: run {
                        Toast.makeText(requireContext(), R.string.current_location_unknown, Toast.LENGTH_LONG).show()
                    }
                }
            }
            R.id.toggle_layers -> {
                // access map on the main thread
                markerManagerLiveData?.value?.mapboxMap?.let { map ->
                    val mapLayersFragment = MapboxLayersFragment.newInstance(map.cameraPosition.target, map.cameraPosition.zoom, MAP_PREFERENCE_NAMESPACE)
                    requireFragmentManager()
                            .beginTransaction()
                            .replace(R.id.mainFrame, mapLayersFragment)
                            .addToBackStack(MapboxLayersFragment::class.java.name)
                            .commit()
                }
            }
            R.id.action_help -> {
                HelpUtil.startHelpActivity(requireContext())
            }
        }
        return true
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        markerManagerLiveData.value?.getCollectItem(marker)?.let { collectItem ->

            collectCardVm.visibility = true
            collectCardVm.itemData.set(collectItem)
        }

        return true
    }

    override fun onMapClick(point: com.mapbox.mapboxsdk.geometry.LatLng) {
        if (collectCardVm.visibility) {
            collectCardVm.visibility = false
        }
    }

    private fun showCollectAddScreen(isLandmark: Boolean) {
        requireFragmentManager()
                .beginTransaction()
                .replace(R.id.mainFrame, CollectAddFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean(CollectAddFragment.IS_LANDMARK, isLandmark)
                    }
                })
                .addToBackStack(CollectAddFragment::class.java.name)
                .commit()
    }


    private fun showItemDetailsScreen(item: CollectItem) {
        requireFragmentManager()
                .beginTransaction()
                .replace(R.id.mainFrame, CollectDetailsFragment.newInstance(item))
                .addToBackStack(CollectDetailsFragment::class.java.name)
                .commit()
    }


    companion object {
        const val MAP_PREFERENCE_NAMESPACE = "SHARED_MAPBOX_LAYER_PREFERENCES"
        const val MAP_FRAGMENT_TAG = "collectFragment.MapboxFragment"

        fun newInstance(): Fragment {
            return CollectFragment()
        }
    }
}
