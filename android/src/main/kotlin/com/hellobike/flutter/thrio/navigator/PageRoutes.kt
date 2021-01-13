/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Hellobike Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.hellobike.flutter.thrio.navigator

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.hellobike.flutter.thrio.BooleanCallback
import com.hellobike.flutter.thrio.NullableBooleanCallback
import com.hellobike.flutter.thrio.NullableIntCallback
import io.flutter.embedding.android.ThrioActivity
import java.lang.ref.WeakReference

internal object PageRoutes : Application.ActivityLifecycleCallbacks {

    private var prevLastRoute: PageRoute? = null

    internal var lastRoute: PageRoute? = null
        set(value) {
            if (field != value) {
                if (field != null) {
                    prevLastRoute = field
                }
                field = value
            }
        }

    val routeHolders by lazy { mutableListOf<PageRouteHolder>() }
    val firstRouteHolder: PageRouteHolder? get() = routeHolders.firstOrNull()

    private val removedRouteHolders by lazy { mutableListOf<PageRouteHolder>() }

    fun lastRouteHolder(pageId: Int): PageRouteHolder? {
        return routeHolders.lastOrNull { it.pageId == pageId }
    }

    fun lastRouteHolder(url: String? = null, index: Int? = null): PageRouteHolder? {
        return routeHolders.lastOrNull { it.hasRoute(url, index) }
    }

    fun removedByRemoveRouteHolder(pageId: Int): PageRouteHolder? {
        val index = removedRouteHolders.indexOfLast { it.pageId == pageId }
        return if (index != -1) removedRouteHolders.removeAt(index) else null
    }

    fun popToRouteHolders(url: String, index: Int?): List<PageRouteHolder> {
        val holder = routeHolders.lastOrNull { it.lastRoute(url, index) != null }
                ?: return listOf()

        val holderIndex = routeHolders.lastIndexOf(holder)
        return routeHolders.subList(holderIndex + 1, routeHolders.size).toMutableList()
    }

    fun hasRoute(pageId: Int): Boolean {
        return routeHolders.any { it.pageId == pageId && it.hasRoute() }
    }

    fun hasRoute(url: String? = null, index: Int? = null): Boolean = routeHolders.any {
        it.hasRoute(url, index)
    }

    fun lastRoute(url: String? = null, index: Int? = null): PageRoute? {
        if (url == null) {
            return routeHolders.lastOrNull()?.lastRoute()
        }
        for (i in routeHolders.size - 1 downTo 0) {
            val holder = routeHolders[i]
            return holder.lastRoute(url, index) ?: continue
        }
        return null
    }

    fun lastRoute(pageId: Int): PageRoute? {
        val holder = routeHolders.lastOrNull { it.pageId == pageId }
        return holder?.lastRoute()
    }

    fun allRoutes(url: String? = null): List<PageRoute> {
        val allRoutes = mutableListOf<PageRoute>()
        for (i in routeHolders.size - 1 downTo 0) {
            val holder = routeHolders[i]
            allRoutes.addAll(holder.allRoute(url))
        }
        return allRoutes.toList()
    }

    fun push(activity: Activity, route: PageRoute, result: NullableIntCallback) {
        val entrypoint = activity.intent.getEntrypoint()
        val pageId = activity.intent.getPageId()
        var holder = routeHolders.lastOrNull { it.pageId == pageId }
        if (holder == null) {
            holder = PageRouteHolder(pageId, activity.javaClass, entrypoint).apply {
                this.activity = WeakReference(activity)
            }
            routeHolders.add(holder)
        }
        holder.push(route, result)
    }

    fun <T> notify(url: String?,
               index: Int?,
               name: String,
               params: T?,
               result: BooleanCallback) {
        if (!hasRoute(url, index)) {
            result(false)
            return
        }

        var isMatch = false
        routeHolders.forEach { holder ->
            holder.notify<T>(url, index, name, params) {
                if (it) isMatch = true
            }
        }
        result(isMatch)
    }

    fun <T> pop(params: T?, animated: Boolean, inRoot: Boolean = false, result: NullableBooleanCallback) {
        val holder = routeHolders.lastOrNull()
        if (holder == null) {
            result(false)
            return
        }

        Log.e("======================","holder.routes ${holder.routes}")
        if (holder.routes.isEmpty()) {
            Log.e("======================","44444444444444444444")
            holder.activity?.get()?.finish()
            result(true)
        } else {
            Log.e("======================","55555555555555555555")
            holder.pop<T>(params, animated, inRoot) { it ->
                if (it == true) {
                    if (!holder.hasRoute()) {
                        holder.activity?.get()?.let {
                            routeHolders.remove(holder)
                            it.finish()
                        }
                    }
                }
                result(it)
            }
        }
    }

    fun popTo(url: String, index: Int?, animated: Boolean, result: BooleanCallback) {
        val routeHolder = routeHolders.lastOrNull { it.lastRoute(url, index) != null }
        if (routeHolder == null || routeHolder.activity?.get() == null) {
            result(false)
            return
        }

        routeHolder.popTo(url, index, animated) { ret ->
            if (ret) {
                val poppedToIndex = routeHolders.lastIndexOf(routeHolder)
                val removedByPopToHolders = routeHolders.subList(poppedToIndex + 1, routeHolders.size).toMutableList()
                val entrypoints = mutableSetOf<String>()
                for (holder in removedByPopToHolders) {
                    if (holder.entrypoint != routeHolder.entrypoint && holder.entrypoint != NAVIGATION_NATIVE_ENTRYPOINT) {
                        entrypoints.add(holder.entrypoint)
                    }
                }

                // 清理其它引擎的页面
                entrypoints.forEach { entrypoint ->
                    removedByPopToHolders.firstOrNull { holder -> holder.entrypoint == entrypoint }?.let {
                        var poppedToSettings = RouteSettings("/", 1)
                        for (i in poppedToIndex downTo 0) {
                            val poppedToRoute = routeHolders[i].lastRoute(entrypoint)
                            if (poppedToRoute != null) {
                                poppedToSettings = poppedToRoute.settings
                                break
                            }
                        }
                        FlutterEngineFactory.getEngine(entrypoint)?.sendChannel?.onPopTo(poppedToSettings.toArguments()) {}
                    }
                }
            }
            result(ret)
        }
    }

    fun remove(url: String, index: Int?, animated: Boolean, result: BooleanCallback) {
        val holder = routeHolders.lastOrNull { it.lastRoute(url, index) != null }
        if (holder == null) {
            result(false)
            return
        }

        holder.remove(url, index, animated) {
            if (it) {
                if (!holder.hasRoute()) {
                    val activity = holder.activity?.get()
                    if (activity == null) {
                        removedRouteHolders.add(holder)
                    } else {
                        activity.finish()
                    }
                }
            }
            result(it)
        }
    }

    fun didPop(routeSettings: RouteSettings) {
        lastRouteHolder()?.apply {
            didPop(routeSettings)
        }
    }

    fun willAppear(routeSettings: RouteSettings, routeAction: RouteAction) {
        if (routeAction == RouteAction.PUSH) {
            val holder = lastRouteHolder()
            val activity = holder?.activity?.get()
            if (holder == null
                    || (activity is ThrioActivity && FlutterEngineFactory.isMultiEngineEnabled)
                    || (activity is ThrioActivity && holder.routes.count() < 1)
                    || activity !is ThrioActivity) {
                return
            }
            PageObservers.willAppear(routeSettings)
            lastRoute?.let { route ->
                if (route.settings != routeSettings) {
                    PageObservers.willDisappear(route.settings)
                }
            }
        } else if (routeAction == RouteAction.POP_TO) {
            val route = lastRoute(routeSettings.url, routeSettings.index)
            if (route != null && route != lastRoute) {
                PageObservers.willAppear(routeSettings)
                lastRoute?.let {
                    PageObservers.willDisappear(it.settings)
                }
            }
        }
    }

    fun didAppear(routeSettings: RouteSettings, routeAction: RouteAction) {
        if (routeAction == RouteAction.PUSH) {
            val holder = lastRouteHolder()
            val activity = holder?.activity?.get()
            if (holder == null
                    || (activity is ThrioActivity && FlutterEngineFactory.isMultiEngineEnabled)
                    || (activity is ThrioActivity && holder.routes.count() < 1)
                    || activity !is ThrioActivity) {
                return
            }
            PageObservers.didAppear(routeSettings)
            lastRoute?.let {
                if (it.settings != routeSettings) {
                    PageObservers.didDisappear(it.settings)
                }
            }
        } else if (routeAction == RouteAction.POP_TO) {
            val route = lastRoute(routeSettings.url, routeSettings.index)
            if (route != null && route != prevLastRoute) {
                PageObservers.didAppear(routeSettings)
                prevLastRoute?.let {
                    PageObservers.didDisappear(it.settings)
                }
            }
        }
    }

    fun willDisappear(routeSettings: RouteSettings, routeAction: RouteAction) {
        if (routeAction == RouteAction.POP || routeAction == RouteAction.REMOVE) {
            if (lastRoute == null || lastRoute?.settings == routeSettings) {
                val holder = lastRouteHolder(routeSettings.url, routeSettings.index)
                if (holder != null && holder.routes.count() < 2) {
                    return
                }
                PageObservers.willDisappear(routeSettings)
                lastRouteHolder()?.let {
                    if (it.routes.count() > 1) {
                        PageObservers.willAppear(it.routes[it.routes.count() - 2].settings)
                    }
                }
            }
        }
    }

    fun didDisappear(routeSettings: RouteSettings, routeAction: RouteAction) {
        if (routeAction == RouteAction.POP || routeAction == RouteAction.REMOVE) {
            if (lastRoute == null || prevLastRoute?.settings == routeSettings) {
                val holder = lastRouteHolder()
                val activity = holder?.activity?.get()
                if (holder == null
                        || (activity is ThrioActivity && FlutterEngineFactory.isMultiEngineEnabled)
                        || (activity is ThrioActivity && holder.routes.count() < 1)
                        || activity !is ThrioActivity) {
                    return
                }
                PageObservers.didDisappear(routeSettings)
                lastRoute?.let {
                    PageObservers.didAppear(it.settings)
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            var pageId = activity.intent.getPageId()
            if (pageId == NAVIGATION_PAGE_ID_NONE) {
                pageId = activity.hashCode()
                activity.intent.putExtra(NAVIGATION_PAGE_ID_KEY, pageId)
                val entrypoint = activity.intent.getEntrypoint()
                val holder = PageRouteHolder(pageId, activity.javaClass, entrypoint).also {
                    it.activity = WeakReference(activity)
                }
                routeHolders.add(holder)
            }
        } else {
            val pageId = savedInstanceState.getInt(NAVIGATION_PAGE_ID_KEY, NAVIGATION_PAGE_ID_NONE)
            if (pageId != NAVIGATION_PAGE_ID_NONE) {
                activity.intent.putExtra(NAVIGATION_PAGE_ID_KEY, pageId)
                val holder = routeHolders.lastOrNull { it.pageId == pageId }
                holder?.activity = WeakReference(activity)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE) {
            val holder = routeHolders.lastOrNull { it.pageId == pageId }
            holder?.activity = WeakReference(activity)
        }
    }

    override fun onActivityPreResumed(activity: Activity) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE && NavigationController.routeAction != RouteAction.POP_TO) {
            routeHolders.lastOrNull { it.pageId == pageId }?.let {
                activity.intent.getRouteSettings()?.let {
                    PageObservers.willAppear(it)
                }
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE) {
            outState.putInt(NAVIGATION_PAGE_ID_KEY, pageId)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE && NavigationController.routeAction != RouteAction.POP_TO) {
            val holder = routeHolders.lastOrNull { it.pageId == pageId }
            holder?.activity = WeakReference(activity)
            lastRoute = holder?.lastRoute()
            routeHolders.lastOrNull { it.pageId == pageId }?.let {
                activity.intent.getRouteSettings()?.let {
                    PageObservers.didAppear(it)
                }
            }
        }
    }

    override fun onActivityPrePaused(activity: Activity) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE && NavigationController.routeAction != RouteAction.POP_TO) {
            activity.intent.getRouteSettings()?.let {
                PageObservers.willDisappear(it)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE && NavigationController.routeAction != RouteAction.POP_TO) {
            activity.intent.getRouteSettings()?.let {
                PageObservers.didDisappear(it)
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        val pageId = activity.intent.getPageId()
        if (pageId != NAVIGATION_PAGE_ID_NONE) {
            routeHolders.lastOrNull { it.pageId == pageId }?.apply {
                if (activity.isFinishing) {
                    routeHolders.remove(this)
                }
                this.activity = null
            }
        }
    }
}