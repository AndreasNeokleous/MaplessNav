package s1875880.maplessnav

/**
 * Created by Andreas Neokleous.
 */

class  Favourite{

    var id: Int = 0
    var placeName: String ?= null
    var lat : Double = 0.0
    var lng: Double = 0.0
    var point: String?=null

 /*   constructor(id: Int, placeName: String, lat: Double, lng: Double){
        this.id = id
        this.placeName = placeName
        this.lat = lat
        this.lng = lng
    }

    constructor(placeName: String, lat: Double, lng: Double){
        this.placeName = placeName
        this.lat = lat
        this.lng = lng
    }*/

    constructor(placeName: String, point: String){
        this.placeName = placeName
        this.point = point
    }

    constructor(id: Int, placeName: String, point: String){
        this.id = id
        this.placeName = placeName
        this.point = point
    }
}