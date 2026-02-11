#!/usr/bin/env python3
"""
Simple OSRM test script to request a route and print a brief summary.
"""
import json
import urllib.request

def fetch_route(start_lon, start_lat, end_lon, end_lat):
    url = f"https://router.project-osrm.org/route/v1/driving/{start_lon},{start_lat};{end_lon},{end_lat}?overview=full&geometries=geojson"
    print(f"Requesting: {url}")
    with urllib.request.urlopen(url, timeout=15) as r:
        data = json.load(r)
    return data

def summarize(data):
    routes = data.get('routes', [])
    if not routes:
        print('No routes found')
        return
    r = routes[0]
    distance = r.get('distance')
    duration = r.get('duration')
    coords = r.get('geometry', {}).get('coordinates', [])
    print(f"Distance: {distance} meters, Duration: {duration} seconds, Points: {len(coords)}")

if __name__ == '__main__':
    # San Francisco short sample
    start_lon, start_lat = -122.4194, 37.7749
    end_lon, end_lat = -122.4094, 37.7849
    data = fetch_route(start_lon, start_lat, end_lon, end_lat)
    summarize(data)
